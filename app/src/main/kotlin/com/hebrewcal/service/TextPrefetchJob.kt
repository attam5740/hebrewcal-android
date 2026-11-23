package com.hebrewcal.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.hebrewcal.data.CalendarLocation
import com.hebrewcal.data.ChitasSchedule
import com.hebrewcal.data.PrefetchNetwork
import com.hebrewcal.data.PrefetchPlanner
import com.hebrewcal.data.SefariaRepository
import com.hebrewcal.data.TikkunLayout
import com.hebrewcal.data.TikkunLayoutRepository
import com.hebrewcal.data.UserPreferencesRepository
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Downloads the texts the reader will want soon — the coming days of Tehillim
 * and the coming Shabbatot's parshiyot — so they open without a connection.
 *
 * Scheduled through the platform JobScheduler rather than WorkManager: the
 * Wi-Fi-only / any-network policy is then enforced by the system service, so
 * the app never needs ACCESS_NETWORK_STATE (WorkManager's in-process
 * constraint tracker requires it and crashes without it).
 */
object TextPrefetch {
    private const val TAG = "TextPrefetch"

    /** Runs one download pass. Returns false when anything failed and a retry is worthwhile. */
    suspend fun run(context: Context): Boolean {
        val prefs = UserPreferencesRepository(context).preferences.first()
        if (prefs.prefetchNetwork == PrefetchNetwork.OFF) return true

        val repo = SefariaRepository(context)
        val tikkun = TikkunLayoutRepository(context)
        var failures = 0

        // +1 day covers the halachic rollover at nightfall.
        val tehillim = PrefetchPlanner.tehillimRefs(JewishDate(), prefs.prefetchTehillimDays + 1, prefs.showElulTehillim)
        for (ref in tehillim) if (!repo.prefetchText(ref)) failures++

        val diaspora = prefs.location == CalendarLocation.DIASPORA
        repo.currentParsha(diaspora) // keeps the reader's undated calendars cache fresh
        for (date in PrefetchPlanner.shabbatDates(LocalDate.now(), prefs.prefetchParshaWeeks)) {
            val parsha = repo.parshaFor(date, diaspora).getOrNull()
            if (parsha == null) { failures++; continue }
            if (!repo.prefetchText(parsha.sefariaRef)) failures++
            // Scroll-layout data for tikkun mode is best-effort.
            runCatching { TikkunLayout.parseTorahRef(parsha.sefariaRef)?.let { tikkun.linesFor(it) } }
        }

        // The prayer texts for the chosen custom, so the notification buttons work offline.
        var prayerRefs = 0
        if (prefs.prayerButtonsOn) for (spec in com.hebrewcal.data.Tefillot.allPrayerSpecs(prefs.nusach)) {
            val refs = repo.expandRefs(spec).getOrNull()
            if (refs == null) { failures++; continue }
            for (ref in refs) { prayerRefs++; if (!repo.prefetchText(ref)) failures++ }
        }

        // Daily Chitas. The aliyot come from the Shabbat's calendars response, the Tanya ref
        // from the Chitas day's own response; the reader fetches single-aliyah ranges, which
        // the whole-parsha prefetch above does not cover, so all seven are fetched here.
        var chitasDays = 0; var tanyaTexts = 0; var tanyaBlank = 0
        if (prefs.chitasButtonOn) for (offset in 0..prefs.prefetchTehillimDays) {
            val day = LocalDate.now().plusDays(offset.toLong())
            val weekly = repo.parshaFor(ChitasSchedule.parshaDateFor(day), diaspora).getOrNull()
            val daily = repo.parshaFor(day, diaspora).getOrNull()
            if (weekly == null || daily == null) { failures++; continue }
            chitasDays++
            for (aliyah in weekly.aliyot.take(7)) if (aliyah.isNotBlank() && !repo.prefetchText(aliyah)) failures++
            if (daily.tanyaRef.isBlank()) { tanyaBlank++; continue }
            // The same range ref the reader requests (the cache key is the ref string).
            val tomorrow = repo.parshaFor(day.plusDays(1), diaspora).getOrNull()
            val leg = ChitasSchedule.tanyaLegRef(daily, tomorrow)
            if (repo.prefetchText(leg.ref)) tanyaTexts++ else failures++
            // The reader falls back to the start segment when the range is unavailable.
            if (leg.ref != daily.tanyaRef && !repo.prefetchText(daily.tanyaRef)) failures++
        }

        Log.i(TAG, "done: ${tehillim.size} tehillim refs, ${prefs.prefetchParshaWeeks} weeks of parsha, $prayerRefs prayer refs, $chitasDays chitas days, $tanyaTexts tanya texts ($tanyaBlank days without a tanya ref), $failures failures")
        return failures == 0
    }

    internal const val JOB_PERIODIC = 7101
    private const val JOB_NOW = 7102

    private fun networkType(network: PrefetchNetwork) =
        if (network == PrefetchNetwork.ANY) JobInfo.NETWORK_TYPE_ANY else JobInfo.NETWORK_TYPE_UNMETERED

    private fun builder(context: Context, id: Int, network: PrefetchNetwork) =
        JobInfo.Builder(id, ComponentName(context, TextPrefetchJobService::class.java))
            .setRequiredNetworkType(networkType(network))
            .setRequiresBatteryNotLow(true)

    /** (Re)schedules the periodic job for [network]; cancels everything when OFF. */
    fun schedule(context: Context, network: PrefetchNetwork, runNow: Boolean) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        if (network == PrefetchNetwork.OFF) {
            js.cancel(JOB_PERIODIC)
            js.cancel(JOB_NOW)
            return
        }
        val pending = js.getPendingJob(JOB_PERIODIC)
        // Re-scheduling an unchanged job would stop it if it is running right now.
        if (pending == null || pending.networkType != networkType(network)) js.schedule(
            builder(context, JOB_PERIODIC, network)
                .setPeriodic(TimeUnit.HOURS.toMillis(12))
                .setPersisted(true)
                .build()
        )
        if (runNow) js.schedule(
            builder(context, JOB_NOW, network)
                .setBackoffCriteria(TimeUnit.MINUTES.toMillis(10), JobInfo.BACKOFF_POLICY_LINEAR)
                .build()
        )
    }

    /** Reads the saved policy and schedules accordingly (app start). */
    fun scheduleFromPrefs(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                // An earlier build enqueued this through WorkManager; make sure no
                // persisted copy of that (crashing) work survives an upgrade.
                WorkManager.getInstance(context).apply {
                    cancelUniqueWork("text_prefetch_periodic")
                    cancelUniqueWork("text_prefetch_now")
                    cancelUniqueWork("midnight_hebrew_cal_update")
                }
                val prefs = UserPreferencesRepository(context).preferences.first()
                schedule(context, prefs.prefetchNetwork, runNow = false)
            }.onFailure { Log.w(TAG, "scheduling failed", it) }
        }
    }
}

class TextPrefetchJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val ok = runCatching { TextPrefetch.run(applicationContext) }
                .onFailure { Log.w("TextPrefetch", "run failed", it) }
                .getOrDefault(false)
            // Periodic jobs come back on their own; only the one-shot asks for a retry.
            jobFinished(params, !ok && !params.isPeriodic())
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.coroutineContext.cancelChildren()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun JobParameters.isPeriodic() = jobId == TextPrefetch.JOB_PERIODIC
}
