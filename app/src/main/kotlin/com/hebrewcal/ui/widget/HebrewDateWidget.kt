package com.hebrewcal.ui.widget

import android.content.Context
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
import com.hebrewcal.data.*
import com.hebrewcal.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.first
import java.util.*

class HebrewDateWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefsRepo  = UserPreferencesRepository(context)
        val calRepo    = HebrewCalendarRepository()
        val zmanimRepo = ZmanimRepository()

        val prefs    = prefsRepo.preferences.first()
        val dateInfo = calRepo.getDateInfo(Date(), prefs.language, prefs.location)

        val zmanimData = if (prefs.showZmanim &&
            prefs.zmanimLocationSource == ZmanimLocationSource.MANUAL &&
            prefs.zmanimManualLat != 0.0 && prefs.zmanimManualLng != 0.0
        ) {
            zmanimRepo.getZmanim(
                latitude       = prefs.zmanimManualLat,
                longitude      = prefs.zmanimManualLng,
                timeZone       = TimeZone.getDefault(),
                date           = Date(),
                selectedZmanim = prefs.selectedZmanim,
                timeFormat     = prefs.zmanimTimeFormat
            )
        } else null

        provideContent {
            HebrewDateWidgetContent(
                dateInfo   = dateInfo,
                zmanimData = zmanimData,
                prefs      = prefs
            )
        }
    }
}

@Composable
fun HebrewDateWidgetContent(
    dateInfo: HebrewDateInfo,
    zmanimData: ZmanimData?,
    prefs: UserPreferences
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
            .clickable(actionStartActivity<SettingsActivity>()),
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
                if (prefs.showParsha) dateInfo.parshaName else null
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
        }
    }
}

class HebrewDateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HebrewDateWidget()
}
