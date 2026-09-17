package com.hebrewcal.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hebrewcal.data.*
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        val parshaLoading: Boolean
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
        val d = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
        val lat = p.zmanimManualLat
        val lng = p.zmanimManualLng
        val tz = TimeZone.getDefault()
        val hasLoc = lat != 0.0 || lng != 0.0

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
            selectedZmanim = p.selectedZmanim, timeFormat = p.zmanimTimeFormat
        ).entries
        val locale = if (p.language == CalendarLanguage.HEBREW) Locale("he") else Locale.ENGLISH
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val jd = JewishDate(d)
        val tehillim = TehillimSchedule.portionFor(jd.jewishDayOfMonth, jd.daysInJewishMonth)
        val elul = if (!p.showElulTehillim) null
        else ElulTehillim.portionFor(jd.jewishMonth, jd.jewishDayOfMonth)?.sefariaRef

        _detail.value = DayDetail(info, weekday, zm, hasLoc, tehillim, elul, null, parshaLoading = true)
        detailJob = viewModelScope.launch {
            val shabbat = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            val pr = sefaria.parshaFor(shabbat, p.location == CalendarLocation.DIASPORA).getOrNull()
            _detail.value = _detail.value?.copy(parsha = pr, parshaLoading = false)
        }
    }
}
