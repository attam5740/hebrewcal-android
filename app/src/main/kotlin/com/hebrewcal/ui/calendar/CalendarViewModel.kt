package com.hebrewcal.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hebrewcal.data.*
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * State for the calendar screen: which month is shown (Hebrew or Gregorian anchored)
 * and the detail bundle for a tapped day. Both live here so they survive rotation.
 */
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val prefsRepo = UserPreferencesRepository(app)
    private val calRepo = HebrewCalendarRepository()
    private val zmanimRepo = ZmanimRepository()
    private val sefaria = SefariaRepository(app)

    val prefs: StateFlow<UserPreferences> = prefsRepo.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    /** Which month the grid is showing. [hebrewMode] picks which anchor pair is used. */
    data class View(
        val hebrewMode: Boolean = true,
        val hYear: Int,
        val hMonth: Int,
        val gYear: Int,
        val gMonth: Int
    )

    private val _view = MutableStateFlow(
        JewishDate().let { jd ->
            val now = LocalDate.now()
            View(true, jd.jewishYear, jd.jewishMonth, now.year, now.monthValue)
        }
    )
    val view: StateFlow<View> = _view

    fun grid(p: UserPreferences, v: View): MonthGrid {
        val israel = p.location == CalendarLocation.ISRAEL
        val he = p.language == CalendarLanguage.HEBREW
        val today = LocalDate.now()
        return if (v.hebrewMode) CalendarMonths.hebrewMonth(v.hYear, v.hMonth, israel, he, today)
        else CalendarMonths.gregorianMonth(v.gYear, v.gMonth, israel, he, today)
    }

    fun step(delta: Int) {
        val v = _view.value
        _view.value = if (v.hebrewMode) {
            val (y, m) = if (delta > 0) CalendarMonths.nextHebrewMonth(v.hYear, v.hMonth)
            else CalendarMonths.prevHebrewMonth(v.hYear, v.hMonth)
            v.copy(hYear = y, hMonth = m)
        } else {
            LocalDate.of(v.gYear, v.gMonth, 1).plusMonths(delta.toLong())
                .let { v.copy(gYear = it.year, gMonth = it.monthValue) }
        }
    }

    fun today() {
        val jd = JewishDate()
        val now = LocalDate.now()
        _view.value = _view.value.copy(
            hYear = jd.jewishYear, hMonth = jd.jewishMonth,
            gYear = now.year, gMonth = now.monthValue
        )
    }

    fun toggleMode() {
        _view.value = _view.value.copy(hebrewMode = !_view.value.hebrewMode)
    }

    /** Everything the bottom sheet renders for one selected day. */
    data class DayDetail(
        val info: HebrewDateInfo,
        val weekday: String,
        val zmanim: List<ZmanEntry>,
        val hasLocation: Boolean,
        val tehillim: TehillimPortion,
        val elulRef: String?,
        val parsha: ParshaRef?,
        val parshaLoading: Boolean,
        val shabbatTimes: ShabbatTimesResult?,
        val celestialArc: CelestialArcData?,
        val moonInfo: MoonPhaseInfo,
        val birkatHaLevana: BirkatHaLevanaInfo,
        val latitude: Double,
        val longitude: Double
    )

    private val _detail = MutableStateFlow<DayDetail?>(null)
    val detail: StateFlow<DayDetail?> = _detail
    private var detailJob: Job? = null

    /** Opens the day sheet for [date]; `null` dismisses it. */
    fun select(date: LocalDate?) {
        detailJob?.cancel()
        if (date == null) {
            _detail.value = null
            return
        }
        val p = prefs.value
        detailJob = viewModelScope.launch {
            // The date/zmanim/fast bundle is synchronous KosherJava work; computing it on
            // Dispatchers.Default keeps the tap off the main thread. It lands well inside a
            // frame budget, so the sheet simply opens once it is ready rather than flashing
            // a skeleton. The parsha (network/cache) follows as a second emission.
            val base = withContext(Dispatchers.Default) { dayDetail(date, p) }
            _detail.value = base
            val shabbat = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            val pr = sefaria.parshaFor(shabbat, p.location == CalendarLocation.DIASPORA).getOrNull()
            _detail.value = _detail.value?.copy(parsha = pr, parshaLoading = false)
        }
    }

    /** Pure (but blocking) build of everything in the day sheet except the parsha. */
    private fun dayDetail(date: LocalDate, p: UserPreferences): DayDetail {
        val d = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
        val lat = p.zmanimManualLat
        val lng = p.zmanimManualLng
        val tz = TimeZone.getDefault()
        val hasLoc = lat != 0.0 || lng != 0.0
        val isHebrew = p.language == CalendarLanguage.HEBREW
        val isIsrael = p.location == CalendarLocation.ISRAEL

        val info = calRepo.getDateInfo(
            date = d,
            language = p.language,
            location = p.location,
            showParshaOnWeekdays = true,
            fastTimes = { kind, fd ->
                if (!hasLoc) null else zmanimRepo.getFastTimes(kind, lat, lng, tz, fd)?.let {
                    FastSchedule.label(
                        zmanimRepo.formatTime(it.begin, p.zmanimTimeFormat),
                        zmanimRepo.formatTime(it.end, p.zmanimTimeFormat)
                    )
                }
            }
        )
        // No past/next highlighting: the selected day is often not today.
        val zm = if (!hasLoc) emptyList() else zmanimRepo.getZmanim(
            latitude = lat, longitude = lng, timeZone = tz, date = d,
            selectedZmanim = p.selectedZmanim, timeFormat = p.zmanimTimeFormat,
            israel = isIsrael, candleLightingOffsetMinutes = p.candleLightingOffsetMinutes,
            havdalahOpinion = p.havdalahOpinion
        ).entries

        val shabbatTimes = if (!hasLoc) null else ShabbatTimes.calculate(
            date = d,
            latitude = lat,
            longitude = lng,
            timeZone = tz,
            israel = isIsrael,
            candleLightingOffsetMinutes = p.candleLightingOffsetMinutes,
            havdalahOpinion = p.havdalahOpinion
        )

        val celestialArc = if (!hasLoc || !p.showCelestialArc) null else CelestialCalculator.calculate(
            latitude = lat,
            longitude = lng,
            timeZone = tz,
            targetDate = d,
            isHebrew = isHebrew
        )

        val jd = JewishDate(d)
        val moonInfo = LunarCalculator.getMoonPhase(
            dayOfMonth = jd.jewishDayOfMonth,
            daysInMonth = jd.daysInJewishMonth,
            isHebrew = isHebrew
        )

        val birkatHaLevana = if (!hasLoc) BirkatHaLevanaInfo(false, null, null, "", "")
        else LunarCalculator.calculateBirkatHaLevana(
            date = d,
            latitude = lat,
            longitude = lng,
            timeZone = tz,
            isHebrew = isHebrew
        )

        val locale = if (isHebrew) Locale("he") else Locale.ENGLISH
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val tehillim = TehillimSchedule.portionFor(jd.jewishDayOfMonth, jd.daysInJewishMonth)
        val elul = if (!p.showElulTehillim) null
        else ElulTehillim.portionFor(jd.jewishMonth, jd.jewishDayOfMonth)?.sefariaRef

        return DayDetail(
            info = info,
            weekday = weekday,
            zmanim = zm,
            hasLocation = hasLoc,
            tehillim = tehillim,
            elulRef = elul,
            parsha = null,
            parshaLoading = true,
            shabbatTimes = shabbatTimes,
            celestialArc = celestialArc,
            moonInfo = moonInfo,
            birkatHaLevana = birkatHaLevana,
            latitude = lat,
            longitude = lng
        )
    }
}
