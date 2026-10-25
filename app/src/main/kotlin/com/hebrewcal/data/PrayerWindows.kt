package com.hebrewcal.data

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Time-limited prayer shortcuts offered as notification buttons; [key] is the stored setting. */
enum class PrayerButton(val key: String) {
    // Declared in the order they occur in a day; notification actions follow this order.
    BIRCHOT_HASHACHAR("birchot_hashachar"),
    SHACHARIT("shacharit"),
    MINCHA("mincha"),
    CANDLE_LIGHTING("candle_lighting"),
    HAVDALAH("havdalah"),
    ARVIT("arvit"),
    SHEMA_AL_HAMITA("shema_al_hamita");

    companion object {
        val ALL_KEYS: Set<String> = values().map { it.key }.toSet()
    }
}

/** Which candle-lighting text applies: Shabbat, Yom Tov, or Shabbat that is also Yom Tov. */
enum class CandleKind { SHABBAT, YOM_TOV, SHABBAT_YOM_TOV }

/** Which version of a service to show: weekday, Friday afternoon, or Shabbat. */
enum class ServiceDay { WEEKDAY, EREV_SHABBAT, SHABBAT }

/**
 * When each prayer button is offered, using the same zmanim the app shows:
 * - Birchot HaShachar: alot hashachar until sof zman shema (GRA)
 * - Candle lighting: one hour either side of candle-lighting time (Friday, Erev Yom Tov)
 * - Havdalah: Motzei Shabbat, for three hours from Havdalah time; not when Yom Tov follows
 *   (then Havdalah is said inside Kiddush with a different text)
 * - Kriat Shema al HaMita: tzet hakochavim until the next alot hashachar
 * The three services use the widest times halacha allows, after the fact included:
 * - Shacharit: alot hashachar until chatzot
 * - Mincha: mincha gedola until tzet hakochavim
 * - Arvit: plag hamincha until the next alot hashachar. On Friday that is the Shabbat
 *   service (accepting Shabbat early); on Saturday it starts at tzet, since a weekday
 *   Arvit cannot be said before Shabbat ends.
 */
object PrayerWindows {
    const val HAVDALAH_LENGTH_MS = 3 * 60 * 60 * 1000L
    const val CANDLE_MARGIN_MS = 60 * 60 * 1000L

    data class Window(
        val button: PrayerButton, val start: Date, val end: Date,
        val candleKind: CandleKind? = null, val serviceDay: ServiceDay? = null
    )
    data class Status(val active: List<Window>, val nextChange: Date?)

    fun status(
        now: Date, latitude: Double, longitude: Double, timeZone: TimeZone, israel: Boolean,
        candleLightingOffsetMinutes: Int, havdalahOpinion: HavdalahOpinion, enabled: Set<PrayerButton>
    ): Status {
        if ((latitude == 0.0 && longitude == 0.0) || enabled.isEmpty()) return Status(emptyList(), null)
        // Yesterday covers windows that run past midnight; tomorrow finds the next openings.
        val windows = (-1..1).flatMap { offset ->
            val day = Calendar.getInstance(timeZone).apply { time = now; add(Calendar.DATE, offset) }.time
            windowsFor(day, latitude, longitude, timeZone, israel, candleLightingOffsetMinutes, havdalahOpinion)
        }.filter { it.button in enabled }
        val active = windows.filter { !now.before(it.start) && now.before(it.end) }
            .distinctBy { it.button }.sortedBy { it.button.ordinal }
        val next = windows.flatMap { listOf(it.start, it.end) }.filter { it.after(now) }.minOrNull()
        return Status(active, next)
    }

    /** Every window that opens on [day] (civil date). */
    fun windowsFor(
        day: Date, latitude: Double, longitude: Double, timeZone: TimeZone, israel: Boolean,
        candleLightingOffsetMinutes: Int, havdalahOpinion: HavdalahOpinion
    ): List<Window> {
        val geo = GeoLocation("User Location", latitude, longitude, 0.0, timeZone)
        val today = ComplexZmanimCalendar(geo).apply { calendar.time = day }
        val nextDay = Calendar.getInstance(timeZone).apply { time = day; add(Calendar.DATE, 1) }.time
        val tomorrow = ComplexZmanimCalendar(geo).apply { calendar.time = nextDay }
        val jcal = JewishCalendar(day).apply { inIsrael = israel }
        val shabbat = ShabbatTimes.calculate(
            date = day, latitude = latitude, longitude = longitude, timeZone = timeZone, israel = israel,
            candleLightingOffsetMinutes = candleLightingOffsetMinutes, havdalahOpinion = havdalahOpinion
        )
        val out = mutableListOf<Window>()

        val friday = jcal.dayOfWeek == Calendar.FRIDAY
        val saturday = jcal.dayOfWeek == Calendar.SATURDAY
        val alot = today.alosHashachar
        val sofShema = today.sofZmanShmaGRA
        if (alot != null && sofShema != null) out += Window(PrayerButton.BIRCHOT_HASHACHAR, alot, sofShema)

        val chatzot = today.chatzos
        if (alot != null && chatzot != null) out += Window(
            PrayerButton.SHACHARIT, alot, chatzot,
            serviceDay = if (saturday) ServiceDay.SHABBAT else ServiceDay.WEEKDAY
        )

        val minchaGedola = today.minchaGedola
        val tzetToday = today.tzais
        if (minchaGedola != null && tzetToday != null) out += Window(
            PrayerButton.MINCHA, minchaGedola, tzetToday,
            serviceDay = when { saturday -> ServiceDay.SHABBAT; friday -> ServiceDay.EREV_SHABBAT; else -> ServiceDay.WEEKDAY }
        )

        val arvitStart = if (saturday) tzetToday else today.plagHamincha
        val dawnAfter = tomorrow.alosHashachar
        if (arvitStart != null && dawnAfter != null) out += Window(
            PrayerButton.ARVIT, arvitStart, dawnAfter,
            serviceDay = if (friday) ServiceDay.SHABBAT else ServiceDay.WEEKDAY
        )

        shabbat.candleLighting?.let { cl ->
            val yomTovTomorrow = JewishCalendar(nextDay).apply { inIsrael = israel }.isYomTovAssurBemelacha
            val kind = when {
                friday && yomTovTomorrow -> CandleKind.SHABBAT_YOM_TOV
                friday -> CandleKind.SHABBAT
                else -> CandleKind.YOM_TOV
            }
            out += Window(
                PrayerButton.CANDLE_LIGHTING,
                Date(cl.time - CANDLE_MARGIN_MS), Date(cl.time + CANDLE_MARGIN_MS), kind
            )
        }

        if (saturday && !jcal.isTomorrowShabbosOrYomTov) {
            shabbat.havdalah?.let { h ->
                out += Window(PrayerButton.HAVDALAH, h, Date(h.time + HAVDALAH_LENGTH_MS))
            }
        }

        val tzet = today.tzais
        val nextAlot = tomorrow.alosHashachar
        if (tzet != null && nextAlot != null) out += Window(PrayerButton.SHEMA_AL_HAMITA, tzet, nextAlot)

        return out
    }
}
