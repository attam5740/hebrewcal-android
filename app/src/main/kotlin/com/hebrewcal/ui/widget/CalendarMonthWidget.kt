package com.hebrewcal.ui.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.hebrewcal.R
import com.hebrewcal.data.CalendarLanguage
import com.hebrewcal.data.CalendarLocation
import com.hebrewcal.data.CalendarMonths
import com.hebrewcal.data.MonthAnchor
import com.hebrewcal.data.MonthGrid
import com.hebrewcal.data.UserPreferences
import com.hebrewcal.data.UserPreferencesRepository
import com.hebrewcal.data.WidgetMonth
import com.hebrewcal.ui.calendar.CalendarActivity
import com.hebrewcal.ui.calendar.EXTRA_DATE
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private const val TAG = "CalendarMonthWidget"
private val K_HEBREW = booleanPreferencesKey("month_hebrew")
private val K_YEAR = intPreferencesKey("month_year")
private val K_MONTH = intPreferencesKey("month_month")
private val K_DAY = longPreferencesKey("month_set_on_epoch_day")
private val OP = ActionParameters.Key<String>("month_op")

/**
 * The month shown by one widget instance. A month chosen by paging is kept only for the day it
 * was chosen, so the widget is back on the current month the next day; the Hebrew/Gregorian
 * choice is kept.
 */
private fun anchorFrom(s: Preferences, today: LocalDate): MonthAnchor {
    val hebrew = s[K_HEBREW] ?: true
    val y = s[K_YEAR]; val m = s[K_MONTH]
    return if (y != null && m != null && s[K_DAY] == today.toEpochDay()) MonthAnchor(hebrew, y, m)
    else WidgetMonth.current(hebrew, today)
}

class CalendarMonthWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = runCatching { UserPreferencesRepository(context).preferences.first() }
            .onFailure { if (it is CancellationException) throw it }
            .onFailure { Log.e(TAG, "widget prefs read failed; using defaults", it) }
            .getOrDefault(UserPreferences())

        provideContent {
            val today = LocalDate.now()
            val anchor = anchorFrom(currentState<Preferences>(), today)
            val grid = runCatching {
                val israel = prefs.location == CalendarLocation.ISRAEL
                val he = prefs.language == CalendarLanguage.HEBREW
                if (anchor.hebrew) CalendarMonths.hebrewMonth(anchor.year, anchor.month, israel, he, today)
                else CalendarMonths.gregorianMonth(anchor.year, anchor.month, israel, he, today)
            }.onFailure { Log.e(TAG, "month grid failed", it) }.getOrNull()
            MonthContent(prefs, anchor, grid)
        }
    }
}

@Composable
private fun MonthContent(prefs: UserPreferences, anchor: MonthAnchor, grid: MonthGrid?) {
    val hebrew = prefs.language == CalendarLanguage.HEBREW
    val gold = ColorProvider(Color(0xFFFFD255))
    val softGold = ColorProvider(Color(0xFFD4AF37))
    val parchment = ColorProvider(Color(0xFFE8D5B7))
    val muted = ColorProvider(Color(0xFFAA9977))
    val dim = ColorProvider(Color(0x66AA9977))
    val sky = ColorProvider(Color(0xFF7EC8E3))
    val gematria = HebrewDateFormatter().apply { isUseGershGershayim = true }
    fun hNum(n: Int) = if (hebrew) gematria.formatHebrewNumber(n) else n.toString()

    Box(
        GlanceModifier.fillMaxSize().background(ImageProvider(widgetBackgroundRes(prefs.widgetOpacity))),
        contentAlignment = Alignment.TopStart
    ) {
        Column(GlanceModifier.fillMaxSize().padding(8.dp)) {
            // Header: ‹ title / subtitle (tap: other calendar) today ›
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹", style = TextStyle(color = parchment, fontSize = TextUnit(24f, TextUnitType.Sp)),
                    modifier = GlanceModifier.padding(horizontal = 10.dp).clickable(navigate("prev"))
                )
                Column(
                    GlanceModifier.defaultWeight().clickable(navigate("toggle")),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        grid?.title ?: "", maxLines = 1,
                        style = TextStyle(color = parchment, fontSize = TextUnit(16f, TextUnitType.Sp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    )
                    Text(
                        grid?.subtitle ?: "", maxLines = 1,
                        style = TextStyle(color = muted, fontSize = TextUnit(10f, TextUnitType.Sp), textAlign = TextAlign.Center)
                    )
                }
                Text(
                    if (hebrew) "היום" else "Today",
                    style = TextStyle(color = gold, fontSize = TextUnit(12f, TextUnitType.Sp), fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(horizontal = 6.dp).clickable(navigate("today"))
                )
                Text(
                    "›", style = TextStyle(color = parchment, fontSize = TextUnit(24f, TextUnitType.Sp)),
                    modifier = GlanceModifier.padding(horizontal = 10.dp).clickable(navigate("next"))
                )
            }
            // Week starts on Sunday, matching CalendarMonths' grid padding.
            val days = if (hebrew) listOf("א", "ב", "ג", "ד", "ה", "ו", "ש") else listOf("S", "M", "T", "W", "T", "F", "S")
            Row(GlanceModifier.fillMaxWidth()) {
                days.forEach {
                    Text(
                        it, modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = muted, fontSize = TextUnit(10f, TextUnitType.Sp), textAlign = TextAlign.Center)
                    )
                }
            }
            // One row per week; Glance containers hold at most 10 children, so rows (<= 6) and
            // cells (7) each stay well inside the limit.
            grid?.cells?.chunked(7)?.forEach { week ->
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    week.forEach { c ->
                        val festive = (c.holidayIndex != -1 && c.holidayIndex != JewishCalendar.ROSH_CHODESH)
                        val numColor = when {
                            !c.inMonth -> dim
                            festive -> gold
                            c.isRoshChodesh -> sky
                            c.isShabbat -> softGold
                            else -> parchment
                        }
                        val big = if (anchor.hebrew) hNum(c.hebrewDay) else c.date.dayOfMonth.toString()
                        val small = if (anchor.hebrew) c.date.dayOfMonth.toString() else hNum(c.hebrewDay)
                        Column(
                            GlanceModifier.defaultWeight().fillMaxHeight()
                                .let { if (c.isToday) it.background(ImageProvider(R.drawable.bg_cal_today)) else it }
                                .clickable(
                                    actionStartActivity<CalendarActivity>(
                                        actionParametersOf(ActionParameters.Key<String>(EXTRA_DATE) to c.date.toString())
                                    )
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                big, maxLines = 1,
                                style = TextStyle(color = numColor, fontSize = TextUnit(14f, TextUnitType.Sp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            )
                            Text(
                                small, maxLines = 1,
                                style = TextStyle(color = if (c.inMonth) muted else dim, fontSize = TextUnit(9f, TextUnitType.Sp), textAlign = TextAlign.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun navigate(op: String) = actionRunCallback<MonthNavCallback>(actionParametersOf(OP to op))

/** ‹ › Today and the title tap: change this widget's month in its own state, then redraw it. */
class MonthNavCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val op = parameters[OP] ?: return
        val today = LocalDate.now()
        updateAppWidgetState(context, glanceId) { p ->
            val cur = anchorFrom(p, today)
            val next = when (op) {
                "prev" -> WidgetMonth.step(cur, -1)
                "next" -> WidgetMonth.step(cur, 1)
                "today" -> WidgetMonth.current(cur.hebrew, today)
                "toggle" -> WidgetMonth.toggled(cur)
                else -> cur
            }
            p[K_HEBREW] = next.hebrew; p[K_YEAR] = next.year; p[K_MONTH] = next.month
            p[K_DAY] = today.toEpochDay()
        }
        CalendarMonthWidget().update(context, glanceId)
    }
}

class CalendarMonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarMonthWidget()
}
