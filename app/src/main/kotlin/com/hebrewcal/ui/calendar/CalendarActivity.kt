package com.hebrewcal.ui.calendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebrewcal.data.CalendarLanguage
import com.hebrewcal.ui.settings.SettingsActivity
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar

/** Translucent month grid: both dates per cell, tap a day for the detail sheet. */
class CalendarActivity : ComponentActivity() {

    private val vm: CalendarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by vm.prefs.collectAsState()
            val view by vm.view.collectAsState()
            val detail by vm.detail.collectAsState()
            val hebrew = prefs.language == CalendarLanguage.HEBREW
            val grid = remember(prefs, view) { vm.grid(prefs, view) }
            val gematria = remember { HebrewDateFormatter().apply { isUseGershGershayim = true } }
            fun hNum(n: Int) = if (hebrew) gematria.formatHebrewNumber(n) else n.toString()

            Box(Modifier.fillMaxSize().background(SCRIM)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.step(-1) }) { Text("‹", color = INACTIVE, fontSize = 26.sp) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(grid.title, color = Color.White, fontSize = 20.sp)
                            Text(grid.subtitle, color = INACTIVE, fontSize = 12.sp)
                        }
                        TextButton(onClick = { vm.step(1) }) { Text("›", color = INACTIVE, fontSize = 26.sp) }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Jump back to the current month: a labeled outlined pill, not a bare dot.
                        TextButton(onClick = { vm.today() }) {
                            Text(
                                if (hebrew) "היום" else "Today",
                                color = ACTIVE_GOLD, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .border(1.dp, ACTIVE_GOLD.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                        TextButton(onClick = { vm.toggleMode() }) {
                            Text(
                                if (view.hebrewMode) "ע" else "G",
                                color = ACTIVE_GOLD, fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = {
                            startActivity(Intent(this@CalendarActivity, SettingsActivity::class.java))
                        }) { Text("⚙", color = INACTIVE, fontSize = 16.sp) }
                    }
                    // Week starts on Sunday, matching CalendarMonths' grid padding.
                    val days = if (hebrew) listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")
                    else listOf("S", "M", "T", "W", "T", "F", "S")
                    Row(Modifier.fillMaxWidth()) {
                        days.forEach {
                            Text(
                                it, color = INACTIVE, fontSize = 11.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 0.5.dp)
                    var drag by remember { mutableStateOf(0f) }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (drag < -80f) vm.step(1) else if (drag > 80f) vm.step(-1)
                                    drag = 0f
                                },
                                onDragCancel = { drag = 0f }
                            ) { _, dx -> drag += dx }
                        }
                    ) {
                        items(grid.cells) { c ->
                            val alpha = if (c.inMonth) 1f else 0.35f
                            Column(
                                Modifier.aspectRatio(0.85f).padding(2.dp)
                                    .background(
                                        if (c.isShabbat && c.inMonth) Color.White.copy(alpha = 0.07f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .then(
                                        if (c.isToday) Modifier.border(1.dp, ACTIVE_GOLD, RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .clickable { vm.select(c.date) }
                                    .padding(top = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (view.hebrewMode) hNum(c.hebrewDay) else c.date.dayOfMonth.toString(),
                                    color = Color.White.copy(alpha = alpha), fontSize = 17.sp
                                )
                                Text(
                                    if (view.hebrewMode) c.date.dayOfMonth.toString() else hNum(c.hebrewDay),
                                    color = INACTIVE.copy(alpha = alpha * 0.8f), fontSize = 10.sp
                                )
                                Row {
                                    if (c.holidayIndex != -1 && c.holidayIndex != JewishCalendar.ROSH_CHODESH) {
                                        Box(Modifier.size(5.dp).background(ACTIVE_GOLD, CircleShape))
                                    }
                                    if (c.isRoshChodesh) {
                                        Box(Modifier.padding(start = 2.dp).size(5.dp).background(ROSH_CHODESH, CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
                detail?.let { d ->
                    DaySheet(d, hebrew, prefs, onDismiss = { vm.select(null) }, onOpen = { startActivity(it) })
                }
            }
        }
    }
}

internal val ACTIVE_GOLD = Color(0xFFFFD255)
internal val INACTIVE = Color(0xE6FFFFFF)
internal val SCRIM = Color(0xFF2E2E2E).copy(alpha = 0.5f)
private val ROSH_CHODESH = Color(0xFF7EC8E3)
