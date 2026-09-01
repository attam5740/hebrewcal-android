package com.hebrewcal.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.glance.appwidget.updateAll
import com.hebrewcal.data.*
import com.hebrewcal.receiver.ZmanAlarmReceiver
import com.hebrewcal.ui.notification.LockscreenNotificationBuilder
import com.hebrewcal.ui.widget.HebrewDateWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*

class CalendarNotificationService : Service() {

    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> /* keep service alive */ }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    private val calRepo = HebrewCalendarRepository()
    private val zmanimRepo = ZmanimRepository()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            LockscreenNotificationBuilder.createNotificationChannel(this)
            val placeholder = LockscreenNotificationBuilder.build(
                this,
                HebrewDateInfo("", "", null, null, false, 0, "", 0),
                null, false, true, true
            )
            // Android 14+ requires the foreground service type passed explicitly to startForeground.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    LockscreenNotificationBuilder.NOTIFICATION_ID,
                    placeholder,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(LockscreenNotificationBuilder.NOTIFICATION_ID, placeholder)
            }
        } catch (e: Exception) {
            stopSelf()
            return
        }
        refreshAndSchedule()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshAndSchedule()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        scheduleRestartAlarm()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestartAlarm()
        super.onTaskRemoved(rootIntent)
    }

    private fun scheduleRestartAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(applicationContext, ZmanAlarmReceiver::class.java).apply {
            action = ZmanAlarmReceiver.ACTION_SERVICE_RESTART
        }
        val pi = PendingIntent.getBroadcast(
            applicationContext,
            RESTART_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + RESTART_DELAY_MS,
                pi
            )
        } catch (e: Exception) { /* non-fatal */ }
    }

    private fun refreshAndSchedule() {
        scope.launch {
            try {
                val prefsRepo = UserPreferencesRepository(applicationContext)
                val prefs = prefsRepo.preferences.first()

                val (lat, lng) = resolveLocation(prefs)
                val now = Date()
                val timeZone = TimeZone.getDefault()

                // Today's tzet hakochavim, used so the Hebrew date rolls at nightfall
                // (halachically correct) rather than at civil midnight. Null when no
                // location is available — falls back to civil-midnight rollover.
                val tzetToday = try {
                    zmanimRepo.getTzetHakochavim(lat, lng, timeZone, now)
                } catch (e: Exception) { null }

                val dateInfo = try {
                    calRepo.getDateInfo(
                        date                 = now,
                        language             = prefs.language,
                        location             = prefs.location,
                        showParshaOnWeekdays = prefs.showParshaOnWeekdays,
                        advanceAfter         = tzetToday
                    )
                } catch (e: Exception) {
                    HebrewDateInfo("", "", null, null, false, 0, "", 0)
                }

                val zmanimData = if (prefs.showZmanim && lat != 0.0 && lng != 0.0) {
                    try {
                        zmanimRepo.getZmanim(
                            latitude       = lat,
                            longitude      = lng,
                            timeZone       = timeZone,
                            date           = now,
                            selectedZmanim = prefs.selectedZmanim,
                            timeFormat     = prefs.zmanimTimeFormat
                        )
                    } catch (e: Exception) { null }
                } else null

                val hebrew = prefs.language == CalendarLanguage.HEBREW
                val tehillimPortion = TehillimSchedule.portionFor(
                    dateInfo.hebrewDayNumber, dateInfo.daysInMonth
                )
                val tehillimActionLabel = if (prefs.showTehillimButton) {
                    try { ReaderButtons.tehillimLabel(tehillimPortion, hebrew) } catch (e: Exception) { null }
                } else null
                val parshaActionLabel = if (prefs.showParshaButton) {
                    try {
                        calRepo.upcomingParshaName(now, prefs.location, prefs.language)
                            ?.let { ReaderButtons.parshaLabel(it, hebrew) }
                    } catch (e: Exception) { null }
                } else null

                val notification = LockscreenNotificationBuilder.build(
                    context       = applicationContext,
                    dateInfo      = dateInfo,
                    zmanimData    = zmanimData,
                    showZmanim    = prefs.showZmanim,
                    showGregorian = prefs.showGregorianDate,
                    showParsha    = prefs.showParsha,
                    showOmer      = prefs.showOmer,
                    tehillimActionLabel = tehillimActionLabel,
                    tehillimActionRef   = tehillimPortion.sefariaRef,
                    parshaActionLabel   = parshaActionLabel,
                    diaspora            = prefs.location == CalendarLocation.DIASPORA,
                    languageName        = prefs.language.name
                )
                NotificationManagerCompat.from(applicationContext)
                    .notify(LockscreenNotificationBuilder.NOTIFICATION_ID, notification)

                // Redraw the home-screen widget. Glance's updateAll triggers
                // provideGlance() on every active instance, which re-reads Date()
                // and renders the (now halachically advanced) Hebrew date.
                try {
                    HebrewDateWidget().updateAll(applicationContext)
                } catch (e: Exception) { /* widget not placed yet — non-fatal */ }

                if (prefs.showZmanim && lat != 0.0 && lng != 0.0) {
                    try { scheduleZmanAlarms(lat, lng, prefs) } catch (e: Exception) { /* non-fatal */ }
                }
                try { scheduleMidnightAlarm() } catch (e: Exception) { /* non-fatal */ }
                try { scheduleWidgetTzetAlarm(lat, lng, timeZone, now, tzetToday) } catch (e: Exception) { /* non-fatal */ }
            } catch (e: Exception) {
                // Outer guard — service stays alive even if everything above fails
            }
        }
    }

    private fun resolveLocation(prefs: UserPreferences): Pair<Double, Double> {
        // Always use the stored city coordinates. In GPS mode the user taps "Detect Nearest
        // City" in Settings which resolves GPS → nearest city and saves it to manual prefs.
        // Doing live GPS here in the background is unreliable on OEM ROMs (OPPO, etc.).
        return Pair(prefs.zmanimManualLat, prefs.zmanimManualLng)
    }

    private fun scheduleZmanAlarms(lat: Double, lng: Double, prefs: UserPreferences) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Cancel any existing zman alarms
        cancelZmanAlarms(alarmManager)

        val futureTimes = zmanimRepo.getFutureZmanimTimes(
            latitude       = lat,
            longitude      = lng,
            timeZone       = TimeZone.getDefault(),
            date           = Date(),
            selectedZmanim = prefs.selectedZmanim
        )

        futureTimes.forEachIndexed { index, (key, time) ->
            val intent = Intent(applicationContext, ZmanAlarmReceiver::class.java).apply {
                action = ZmanAlarmReceiver.ACTION_ZMAN_TRANSITION
                putExtra(ZmanAlarmReceiver.EXTRA_ZMAN_KEY, key)
            }
            val pi = PendingIntent.getBroadcast(
                applicationContext,
                ZMAN_ALARM_BASE_REQUEST_CODE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    time.time,
                    pi
                )
            } catch (e: SecurityException) {
                // SCHEDULE_EXACT_ALARM not granted — fall back to inexact
                alarmManager.set(AlarmManager.RTC_WAKEUP, time.time, pi)
            }
        }
    }

    private fun cancelZmanAlarms(alarmManager: AlarmManager) {
        for (i in 0 until 20) {
            val intent = Intent(applicationContext, ZmanAlarmReceiver::class.java).apply {
                action = ZmanAlarmReceiver.ACTION_ZMAN_TRANSITION
            }
            val pi = PendingIntent.getBroadcast(
                applicationContext,
                ZMAN_ALARM_BASE_REQUEST_CODE + i,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { alarmManager.cancel(it) }
        }
    }

    private fun scheduleMidnightAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DATE, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }
        val intent = Intent(applicationContext, ZmanAlarmReceiver::class.java).apply {
            action = ZmanAlarmReceiver.ACTION_MIDNIGHT_REFRESH
        }
        val pi = PendingIntent.getBroadcast(
            applicationContext,
            MIDNIGHT_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight.timeInMillis, pi)
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, midnight.timeInMillis, pi)
        }
    }

    /**
     * Schedules the next widget refresh at tzet hakochavim (nightfall). Today's tzet
     * is used if still in the future; otherwise tomorrow's. When [tzetToday] is null
     * (no location set), the alarm is skipped — the scheduleMidnightAlarm() above
     * already provides a daily civil-midnight refresh as a fallback.
     */
    private fun scheduleWidgetTzetAlarm(
        lat: Double,
        lng: Double,
        timeZone: TimeZone,
        now: Date,
        tzetToday: Date?
    ) {
        val trigger: Date = when {
            tzetToday != null && tzetToday.after(now) -> tzetToday
            tzetToday != null -> {
                val tomorrow = Calendar.getInstance(timeZone).apply {
                    time = now
                    add(Calendar.DATE, 1)
                }.time
                zmanimRepo.getTzetHakochavim(lat, lng, timeZone, tomorrow) ?: return
            }
            else -> return  // No location → midnight alarm covers the daily refresh.
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(applicationContext, ZmanAlarmReceiver::class.java).apply {
            action = ZmanAlarmReceiver.ACTION_WIDGET_REFRESH
        }
        val pi = PendingIntent.getBroadcast(
            applicationContext,
            WIDGET_TZET_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.time, pi)
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, trigger.time, pi)
        }
    }

    companion object {
        const val ZMAN_ALARM_BASE_REQUEST_CODE = 2000
        const val MIDNIGHT_ALARM_REQUEST_CODE = 2100
        const val RESTART_REQUEST_CODE = 2200
        const val WIDGET_TZET_ALARM_REQUEST_CODE = 2300
        const val RESTART_DELAY_MS = 5_000L

        fun start(context: Context) {
            val intent = Intent(context, CalendarNotificationService::class.java)
            context.startForegroundService(intent)
        }

        fun refresh(context: Context) {
            val intent = Intent(context, CalendarNotificationService::class.java)
            context.startForegroundService(intent)
        }
    }
}
