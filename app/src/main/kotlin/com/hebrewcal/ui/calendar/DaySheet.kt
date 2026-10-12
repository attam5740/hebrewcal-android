package com.hebrewcal.ui.calendar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Locale
import com.hebrewcal.data.CalendarLocation
import com.hebrewcal.data.ReaderButtons
import com.hebrewcal.data.ShabbatTimes
import com.hebrewcal.data.UserPreferences
import com.hebrewcal.ui.reader.TextReaderActivity

/**
 * Bottom sheet for one selected day: the two dates, holiday / fast / parsha / omer
 * labels, reader links, and that day's zmanim.
 */
@Composable
fun DaySheet(
    d: CalendarViewModel.DayDetail,
    hebrew: Boolean,
    prefs: UserPreferences,
    onDismiss: () -> Unit,
    onOpen: (Intent) -> Unit
) {
    val ctx = LocalContext.current
    fun reader(
        mode: String, ref: String, title: String, teamim: Boolean, elul: String = "",
        aliyot: List<String> = emptyList()
    ) =
        Intent(ctx, TextReaderActivity::class.java).apply {
            putExtra(TextReaderActivity.EXTRA_MODE, mode)
            putExtra(TextReaderActivity.EXTRA_REF, ref)
            putExtra(TextReaderActivity.EXTRA_TITLE, title)
            putExtra(TextReaderActivity.EXTRA_LANG, prefs.language.name)
            putExtra(TextReaderActivity.EXTRA_NIKKUD, true)
            putExtra(TextReaderActivity.EXTRA_TEAMIM, teamim)
            putExtra(TextReaderActivity.EXTRA_DIASPORA, prefs.location == CalendarLocation.DIASPORA)
            putExtra(TextReaderActivity.EXTRA_ELUL_REF, elul)
            putStringArrayListExtra(TextReaderActivity.EXTRA_ALIYOT, ArrayList(aliyot))
        }

    var showCompass by remember { mutableStateOf(false) }

    if (showCompass && d.hasLocation) {
        MizrachCompassDialog(
            latitude = d.latitude,
            longitude = d.longitude,
            isHebrew = hebrew,
            onDismiss = { showCompass = false }
        )
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66000000)).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) { onDismiss() },
            Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.68f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(Color(0xE0141414))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) {}
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Text(d.info.hebrewDateString, color = Color.White, fontSize = 22.sp)
                Text("${d.weekday} · ${d.info.gregorianDateString}", color = INACTIVE, fontSize = 13.sp)
                val labels = listOfNotNull(
                    d.info.holidayName,
                    d.info.parshaName?.let { if (hebrew) "פרשת $it" else it },
                    d.info.omerText
                )
                if (labels.isNotEmpty()) {
                    Text(
                        labels.joinToString(" · "), color = ACTIVE_GOLD, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                // Shabbat & Chag Highlight Banner
                d.shabbatTimes?.let { st ->
                    if (st.isErevShabbat || st.isErevYomTov) {
                        st.candleLighting?.let { cl ->
                            val timeFormat = SimpleDateFormat(
                                if (prefs.zmanimTimeFormat == com.hebrewcal.data.ZmanimTimeFormat.TWELVE_HOUR) "h:mm a" else "HH:mm",
                                Locale.getDefault()
                            )
                            val clStr = timeFormat.format(cl)
                            Text(
                                "🕯️ " + ShabbatTimes.formatCandleLighting(hebrew, clStr),
                                color = ACTIVE_GOLD,
                                fontSize = 15.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    } else if (st.isShabbat || st.isYomTov) {
                        st.havdalah?.let { hav ->
                            val timeFormat = SimpleDateFormat(
                                if (prefs.zmanimTimeFormat == com.hebrewcal.data.ZmanimTimeFormat.TWELVE_HOUR) "h:mm a" else "HH:mm",
                                Locale.getDefault()
                            )
                            val havStr = timeFormat.format(hav)
                            val rtStr = st.havdalahRt?.let { timeFormat.format(it) }
                            Text(
                                "🍷 " + ShabbatTimes.formatHavdalah(hebrew, havStr, rtStr),
                                color = ACTIVE_GOLD,
                                fontSize = 15.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    } else {
                        // Regular weekday — no Shabbat/Chag banner
                    }
                }

                // Celestial Arc & Shaot Zmaniyot card
                d.celestialArc?.let { arc ->
                    Spacer(Modifier.height(10.dp))
                    CelestialArcView(
                        arcData = arc,
                        moonInfo = d.moonInfo,
                        birkatHaLevana = d.birkatHaLevana,
                        isHebrew = hebrew,
                        onOpenCompass = { showCompass = true },
                        onOpenBirkatHaLevana = {
                            onOpen(reader(
                                "tefillah", "Siddur Ashkenaz, Berachot, Birkat HaLevana",
                                if (hebrew) "ברכת הלבנה" else "Birkat HaLevana", false
                            ))
                        }
                    )
                }

                HorizontalDivider(
                    Modifier.padding(vertical = 10.dp),
                    thickness = 0.5.dp, color = Color.White.copy(alpha = 0.15f)
                )

                val portion = d.tehillim
                val tehillimLabel = ReaderButtons.tehillimLabel(portion, hebrew)
                LinkRow(if (hebrew) "תהילים יומי" else "Daily Tehillim", tehillimLabel) {
                    onOpen(reader("tehillim", portion.sefariaRef, tehillimLabel, false, d.elulRef ?: ""))
                }
                when {
                    d.parshaLoading ->
                        Text(if (hebrew) "טוען פרשה…" else "Loading parsha…", color = INACTIVE, fontSize = 12.sp)
                    d.parsha != null -> {
                        val p = d.parsha
                        LinkRow(if (hebrew) "פרשה" else "Parsha", if (hebrew) p.nameHe else p.nameEn) {
                            onOpen(
                                reader(
                                    "parsha", p.sefariaRef,
                                    if (hebrew) "פרשת ${p.nameHe}" else "Parashat ${p.nameEn}", true,
                                    aliyot = p.aliyot
                                )
                            )
                        }
                        p.haftarot.forEach { h ->
                            val name = if (hebrew) h.nameHe else h.nameEn
                            LinkRow(if (hebrew) "הפטרה" else "Haftarah", name) {
                                onOpen(
                                    reader(
                                        "haftarah", h.dottedRef,
                                        (if (hebrew) "הפטרה · " else "Haftarah · ") + name, true
                                    )
                                )
                            }
                        }
                    }
                    else ->
                        Text(
                            if (hebrew) "אין חיבור לאינטרנט" else "No internet connection",
                            color = INACTIVE, fontSize = 12.sp
                        )
                }
                HorizontalDivider(
                    Modifier.padding(vertical = 10.dp),
                    thickness = 0.5.dp, color = Color.White.copy(alpha = 0.15f)
                )
                if (!d.hasLocation) {
                    Text(
                        if (hebrew) "הגדר עיר בהגדרות לזמני היום" else "Set a city in Settings to see zmanim",
                        color = INACTIVE, fontSize = 12.sp
                    )
                }
                // Plain rows: no past/next highlighting, since this day may not be today.
                d.zmanim.forEach { z ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(z.displayName, color = INACTIVE, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(z.timeString, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = ACTIVE_GOLD, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 14.sp)
        Text("  ›", color = ACTIVE_GOLD, fontSize = 16.sp)
    }
}
