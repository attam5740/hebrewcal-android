package com.hebrewcal.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.hebrewcal.data.*
import com.hebrewcal.data.TehillimSchedule
import com.hebrewcal.data.CalendarLocation
import com.hebrewcal.ui.reader.TextReaderActivity
import com.hebrewcal.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.first
import java.util.*

private fun chapterRangeLabel(first: Int, last: Int, hebrew: Boolean): String {
    val fmt = com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter()
    fun n(x: Int) = if (hebrew) fmt.formatHebrewNumber(x) else x.toString()
    return if (first == last) n(first) else "${n(first)}–${n(last)}"
}

class HebrewDateWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefsRepo  = UserPreferencesRepository(context)
        val calRepo    = HebrewCalendarRepository()
        val zmanimRepo = ZmanimRepository()

        val prefs    = prefsRepo.preferences.first()
        val now      = Date()
        val timeZone = TimeZone.getDefault()

        // Today's tzet hakochavim so the Hebrew date rolls at nightfall, matching the
        // halachic day. Returns null when no location is configured, in which case
        // getDateInfo falls back to civil-midnight rollover.
        val tzetToday = zmanimRepo.getTzetHakochavim(
            latitude  = prefs.zmanimManualLat,
            longitude = prefs.zmanimManualLng,
            timeZone  = timeZone,
            date      = now
        )

        val dateInfo = calRepo.getDateInfo(
            date                 = now,
            language             = prefs.language,
            location             = prefs.location,
            showParshaOnWeekdays = prefs.showParshaOnWeekdays,
            advanceAfter         = tzetToday
        )

        val zmanimData = if (prefs.showZmanim &&
            prefs.zmanimLocationSource == ZmanimLocationSource.MANUAL &&
            prefs.zmanimManualLat != 0.0 && prefs.zmanimManualLng != 0.0
        ) {
            zmanimRepo.getZmanim(
                latitude       = prefs.zmanimManualLat,
                longitude      = prefs.zmanimManualLng,
                timeZone       = timeZone,
                date           = now,
                selectedZmanim = prefs.selectedZmanim,
                timeFormat     = prefs.zmanimTimeFormat
            )
        } else null

        val hebrew = prefs.language == CalendarLanguage.HEBREW
        val portion = TehillimSchedule.portionFor(dateInfo.hebrewDayNumber, dateInfo.daysInMonth)
        val tehillimLabel = (if (hebrew) "תהילים יומי" else "Daily Tehillim") + " · " +
            chapterRangeLabel(portion.firstChapter, portion.lastChapter, hebrew)
        val parshaName = calRepo.upcomingParshaName(now, prefs.location, prefs.language)
        val parshaLabel = parshaName?.let {
            (if (hebrew) "פרשת השבוע" else "Weekly Parsha") + " · " + it
        }
        val diaspora = prefs.location == CalendarLocation.DIASPORA

        provideContent {
            HebrewDateWidgetContent(
                context       = context,
                dateInfo      = dateInfo,
                zmanimData    = zmanimData,
                prefs         = prefs,
                tehillimLabel = tehillimLabel,
                tehillimRef   = portion.sefariaRef,
                parshaLabel   = parshaLabel,
                diaspora      = diaspora
            )
        }
    }
}

@Composable
fun HebrewDateWidgetContent(
    context: Context,
    dateInfo: HebrewDateInfo,
    zmanimData: ZmanimData?,
    prefs: UserPreferences,
    tehillimLabel: String,
    tehillimRef: String,
    parshaLabel: String?,
    diaspora: Boolean
) {
    val bgColor        = ColorProvider(Color(0xFF1A1A2E))
    val goldColor      = ColorProvider(Color(0xFFD4AF37))
    val parchmentColor = ColorProvider(Color(0xFFE8D5B7))
    val mutedColor     = ColorProvider(Color(0xFFAA9977))
    val blueColor      = ColorProvider(Color(0xFF7EC8E3))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(16)
            .clickable(actionStartActivity(Intent(context, SettingsActivity::class.java))),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Hebrew date — large
            Text(
                text  = dateInfo.hebrewDateString,
                style = TextStyle(
                    color      = parchmentColor,
                    fontSize   = TextUnit(22f, TextUnitType.Sp),
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(GlanceModifier.height(2.dp))

            if (prefs.showGregorianDate) {
                Text(
                    text  = dateInfo.gregorianDateString,
                    style = TextStyle(
                        color    = mutedColor,
                        fontSize = TextUnit(13f, TextUnitType.Sp)
                    )
                )
            }

            val subLine = listOfNotNull(
                dateInfo.holidayName,
                if (prefs.showParsha) dateInfo.parshaName else null,
                if (prefs.showOmer && dateInfo.omerDay > 0) dateInfo.omerText else null
            ).joinToString(" · ")

            if (subLine.isNotEmpty()) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text  = subLine,
                    style = TextStyle(
                        color      = goldColor,
                        fontSize   = TextUnit(13f, TextUnitType.Sp),
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            val nextZman = zmanimData?.entries?.firstOrNull { it.isNext }
            if (nextZman != null) {
                Spacer(GlanceModifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = "▶ ${nextZman.displayName}",
                        style = TextStyle(
                            color    = blueColor,
                            fontSize = TextUnit(12f, TextUnitType.Sp)
                        )
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text  = nextZman.timeString,
                        style = TextStyle(
                            color      = blueColor,
                            fontSize   = TextUnit(12f, TextUnitType.Sp),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Glance/RemoteViews containers allow at most 10 children; with all optional
            // rows enabled the outer Column would exceed that, so the pills live in a
            // nested Column (which gets its own child budget).
            Column {
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    text = tehillimLabel,
                    style = TextStyle(color = goldColor, fontSize = TextUnit(12f, TextUnitType.Sp), fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.clickable(
                        actionStartActivity(
                            Intent(context, TextReaderActivity::class.java).apply {
                                putExtra(TextReaderActivity.EXTRA_MODE, "tehillim")
                                putExtra(TextReaderActivity.EXTRA_REF, tehillimRef)
                                putExtra(TextReaderActivity.EXTRA_TITLE, tehillimLabel)
                                putExtra(TextReaderActivity.EXTRA_LANG, prefs.language.name)
                                putExtra(TextReaderActivity.EXTRA_NIKKUD, true)
                                putExtra(TextReaderActivity.EXTRA_TEAMIM, false)
                            }
                        )
                    )
                )
                if (parshaLabel != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = parshaLabel,
                        style = TextStyle(color = goldColor, fontSize = TextUnit(12f, TextUnitType.Sp), fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.clickable(
                            actionStartActivity(
                                Intent(context, TextReaderActivity::class.java).apply {
                                    putExtra(TextReaderActivity.EXTRA_MODE, "parsha")
                                    putExtra(TextReaderActivity.EXTRA_REF, "")
                                    putExtra(TextReaderActivity.EXTRA_TITLE, parshaLabel)
                                    putExtra(TextReaderActivity.EXTRA_DIASPORA, diaspora)
                                    putExtra(TextReaderActivity.EXTRA_LANG, prefs.language.name)
                                    putExtra(TextReaderActivity.EXTRA_NIKKUD, true)
                                    putExtra(TextReaderActivity.EXTRA_TEAMIM, true)
                                }
                            )
                        )
                    )
                }
            }
        }
    }
}

class HebrewDateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HebrewDateWidget()
}
