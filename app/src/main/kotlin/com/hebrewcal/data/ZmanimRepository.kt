package com.hebrewcal.data

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.text.SimpleDateFormat
import java.util.*

data class ZmanEntry(
    val key: String,
    val displayName: String,
    val time: Date?,
    val timeString: String,     // formatted time string
    val isPast: Boolean,
    val isNext: Boolean         // highlighted as next upcoming
)

data class ZmanimData(
    val entries: List<ZmanEntry>,
    val nextZmanKey: String?,
    val nextZmanTime: Date?
)

class ZmanimRepository {

    fun getZmanim(
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        timeZone: TimeZone = TimeZone.getDefault(),
        date: Date = Date(),
        selectedZmanim: Set<String>,
        timeFormat: ZmanimTimeFormat
    ): ZmanimData {
        if (latitude == 0.0 && longitude == 0.0) return ZmanimData(emptyList(), null, null)

        val geoLocation = GeoLocation("User Location", latitude, longitude, elevation, timeZone)
        val cal = ComplexZmanimCalendar(geoLocation).apply {
            calendar.time = date
        }

        val now = Date()
        val formatter = if (timeFormat == ZmanimTimeFormat.TWELVE_HOUR) {
            SimpleDateFormat("h:mm a", Locale.getDefault())
        } else {
            SimpleDateFormat("HH:mm", Locale.getDefault())
        }

        // Build all times in chronological order
        val allTimes: Map<String, Date?> = mapOf(
            ZmanimKeys.ALOT_HASHACHAR     to cal.alosHashachar,
            ZmanimKeys.MISHEYAKIR         to cal.misheyakir11Point5Degrees,
            ZmanimKeys.HANETZ             to cal.sunrise,
            ZmanimKeys.SOF_ZMAN_SHEMA_GRA to cal.sofZmanShmaGRA,
            ZmanimKeys.SOF_ZMAN_SHEMA_MGA to cal.sofZmanShmaMGA,
            ZmanimKeys.SOF_ZMAN_TEFILLA_GRA to cal.sofZmanTfilaGRA,
            ZmanimKeys.SOF_ZMAN_TEFILLA_MGA to cal.sofZmanTfilaMGA,
            ZmanimKeys.CHATZOT            to cal.chatzos,
            ZmanimKeys.MINCHA_GEDOLA      to cal.minchaGedola,
            ZmanimKeys.MINCHA_KETANA      to cal.minchaKetana,
            ZmanimKeys.PLAG_HAMINCHA      to cal.plagHamincha,
            ZmanimKeys.SHKIAH             to cal.sunset,
            ZmanimKeys.TZET_HAKOCHAVIM    to cal.tzais,
            ZmanimKeys.TZET_RABBEINU_TAM  to cal.tzais72
        )

        // Filter to only selected, sort chronologically
        val selected = allTimes
            .filter { (key, time) -> key in selectedZmanim && time != null }
            .entries
            .sortedBy { it.value }

        // Find next upcoming zman
        val nextEntry = selected.firstOrNull { (_, time) -> time != null && time.after(now) }

        val entries = selected.map { (key, time) ->
            val isPast = time != null && time.before(now)
            val isNext = key == nextEntry?.key
            ZmanEntry(
                key         = key,
                displayName = ZmanimKeys.displayName(key),
                time        = time,
                timeString  = time?.let { formatter.format(it) } ?: "—",
                isPast      = isPast,
                isNext      = isNext
            )
        }

        return ZmanimData(
            entries      = entries,
            nextZmanKey  = nextEntry?.key,
            nextZmanTime = nextEntry?.value
        )
    }

    /**
     * Returns list of (key, time) for all selected zmanim in the future,
     * used for scheduling AlarmManager transitions.
     */
    fun getFutureZmanimTimes(
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        timeZone: TimeZone = TimeZone.getDefault(),
        date: Date = Date(),
        selectedZmanim: Set<String>
    ): List<Pair<String, Date>> {
        if (latitude == 0.0 && longitude == 0.0) return emptyList()

        val geoLocation = GeoLocation("User Location", latitude, longitude, elevation, timeZone)
        val cal = ComplexZmanimCalendar(geoLocation).apply {
            calendar.time = date
        }
        val now = Date()

        return mapOf(
            ZmanimKeys.ALOT_HASHACHAR     to cal.alosHashachar,
            ZmanimKeys.MISHEYAKIR         to cal.misheyakir11Point5Degrees,
            ZmanimKeys.HANETZ             to cal.sunrise,
            ZmanimKeys.SOF_ZMAN_SHEMA_GRA to cal.sofZmanShmaGRA,
            ZmanimKeys.SOF_ZMAN_SHEMA_MGA to cal.sofZmanShmaMGA,
            ZmanimKeys.SOF_ZMAN_TEFILLA_GRA to cal.sofZmanTfilaGRA,
            ZmanimKeys.SOF_ZMAN_TEFILLA_MGA to cal.sofZmanTfilaMGA,
            ZmanimKeys.CHATZOT            to cal.chatzos,
            ZmanimKeys.MINCHA_GEDOLA      to cal.minchaGedola,
            ZmanimKeys.MINCHA_KETANA      to cal.minchaKetana,
            ZmanimKeys.PLAG_HAMINCHA      to cal.plagHamincha,
            ZmanimKeys.SHKIAH             to cal.sunset,
            ZmanimKeys.TZET_HAKOCHAVIM    to cal.tzais,
            ZmanimKeys.TZET_RABBEINU_TAM  to cal.tzais72
        )
            .filter { (key, time) -> key in selectedZmanim && time != null && time.after(now) }
            .map { (key, time) -> Pair(key, time!!) }
            .sortedBy { it.second }
    }
}
