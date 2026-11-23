package com.hebrewcal.ui.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.hebrewcal.data.CalendarLanguage
import com.hebrewcal.data.CalendarMonths
import com.hebrewcal.data.MonthAnchor
import com.hebrewcal.data.UserPreferences
import com.hebrewcal.data.UserPreferencesRepository
import com.hebrewcal.data.WidgetMonth
import com.hebrewcal.ui.calendar.CalendarViewModel
import com.hebrewcal.ui.calendar.DateNavigatorDialog
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * A widget cannot show a dialog, so tapping the month widget's heading opens this transparent
 * activity with the app's date navigator over it. Picking a month stores it in that widget's own
 * state and redraws it; dismissing changes nothing.
 */
class MonthPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        if (widgetId == -1) { finish(); return }
        val repo = UserPreferencesRepository(applicationContext)
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(widgetId)
            val today = LocalDate.now()
            val shown = anchorFrom(getAppWidgetState<Preferences>(applicationContext, PreferencesGlanceStateDefinition, glanceId), today)
            // The navigator wants both calendars' anchors; the inactive one is the shown month's twin.
            val other = WidgetMonth.toggled(shown)
            val h = if (shown.hebrew) shown else other
            val g = if (shown.hebrew) other else shown
            val view = CalendarViewModel.View(shown.hebrew, h.year, h.month, g.year, g.month)

            fun choose(next: MonthAnchor) = lifecycleScope.launch {
                updateAppWidgetState(applicationContext, glanceId) { p ->
                    p[K_HEBREW] = next.hebrew; p[K_YEAR] = next.year; p[K_MONTH] = next.month
                    p[K_DAY] = today.toEpochDay()
                }
                CalendarMonthWidget().update(applicationContext, glanceId)
                finish()
            }

            setContent {
                val prefs by repo.preferences.collectAsState(initial = UserPreferences())
                DateNavigatorDialog(
                    view = view,
                    hebrewUi = prefs.language == CalendarLanguage.HEBREW,
                    onPick = { hebrew, y, m -> choose(MonthAnchor(hebrew, y, m)) },
                    onToday = { choose(WidgetMonth.current(shown.hebrew, today)) },
                    onDismiss = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_WIDGET_ID = "widget_id"
    }
}
