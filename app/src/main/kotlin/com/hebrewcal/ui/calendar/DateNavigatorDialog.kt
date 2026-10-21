package com.hebrewcal.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private val GREG_YEARS = 1800..2200
private val HEB_YEARS = 5560..5960

/**
 * Tap-the-heading date navigator: pick a year (step, hold to scrub, or tap to type)
 * and a month, in either calendar, and the grid jumps straight there.
 */
@Composable
fun DateNavigatorDialog(
    view: CalendarViewModel.View,
    hebrewUi: Boolean,
    onPick: (hebrewMode: Boolean, year: Int, month: Int) -> Unit,
    onToday: () -> Unit,
    onDismiss: () -> Unit
) {
    var hebrewMode by remember { mutableStateOf(view.hebrewMode) }
    var hYear by remember { mutableStateOf(view.hYear) }
    var gYear by remember { mutableStateOf(view.gYear) }
    var typing by remember { mutableStateOf(false) }
    val gematria = remember { HebrewDateFormatter() }
    val monthFmt = remember(hebrewUi) { HebrewDateFormatter().apply { isHebrewFormat = hebrewUi } }

    val year = if (hebrewMode) hYear else gYear
    val range = if (hebrewMode) HEB_YEARS else GREG_YEARS
    fun setYear(y: Int) = if (hebrewMode) hYear = y.coerceIn(HEB_YEARS) else gYear = y.coerceIn(GREG_YEARS)

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides if (hebrewUi) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF0262626))
                    .padding(18.dp)
            ) {
                // Calendar toggle, in the style of the grid's ע/G button
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .padding(3.dp).align(Alignment.CenterHorizontally)
                ) {
                    listOf(true to (if (hebrewUi) "עברי" else "Hebrew"), false to (if (hebrewUi) "לועזי" else "Gregorian"))
                        .forEach { (isHeb, label) ->
                            val on = hebrewMode == isHeb
                            Text(
                                label,
                                color = if (on) Color(0xFF1B1B1B) else INACTIVE,
                                fontSize = 13.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (on) ACTIVE_GOLD else Color.Transparent)
                                    .clickable { hebrewMode = isHeb; typing = false }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                }

                Spacer(Modifier.height(14.dp))

                // Year row: step, hold to scrub, tap the year to type it
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    YearArrow("−", onStep = { setYear(year - 1) })
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (typing) {
                            YearField(year, range, hebrewUi) { typed -> setYear(typed); typing = false }
                        } else {
                            Text(
                                if (hebrewMode && hebrewUi) gematria.formatHebrewNumber(year) else year.toString(),
                                color = ACTIVE_GOLD, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { typing = true }.padding(horizontal = 18.dp, vertical = 2.dp)
                            )
                        }
                    }
                    YearArrow("+", onStep = { setYear(year + 1) })
                }

                Spacer(Modifier.height(12.dp))

                // Month grid: 12 Gregorian months, or Tishrei..Elul (13 in a leap year)
                val today = remember { LocalDate.now() }
                val todayJd = remember { JewishDate() }
                val months: List<Pair<Int, String>> = if (hebrewMode) {
                    val leap = JewishDate(year, 7, 1).isJewishLeapYear
                    val order = (7..12).toList() + (if (leap) listOf(13) else emptyList()) + (1..6).toList()
                    order.map { m -> m to monthFmt.formatMonth(JewishDate(year, m, 1)) }
                } else {
                    val locale = if (hebrewUi) Locale("he") else Locale.ENGLISH
                    val style = if (hebrewUi) JTextStyle.FULL_STANDALONE else JTextStyle.SHORT
                    (1..12).map { m -> m to Month.of(m).getDisplayName(style, locale) }
                }
                val shownMonth = if (hebrewMode) (view.hYear to view.hMonth) else (view.gYear to view.gMonth)
                val todayMonth = if (hebrewMode) (todayJd.jewishYear to todayJd.jewishMonth) else (today.year to today.monthValue)
                months.chunked(3).forEach { rowMonths ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowMonths.forEach { (m, name) ->
                            val isShown = (year to m) == shownMonth
                            val isToday = (year to m) == todayMonth
                            Text(
                                name,
                                color = if (isShown) ACTIVE_GOLD else Color.White,
                                fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1,
                                fontWeight = if (isShown || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isToday) Color.White.copy(alpha = 0.09f) else Color.Transparent)
                                    .then(
                                        if (isShown) Modifier.border(1.dp, ACTIVE_GOLD, RoundedCornerShape(9.dp))
                                        else Modifier
                                    )
                                    .clickable { onPick(hebrewMode, year, m) }
                                    .padding(vertical = 9.dp)
                            )
                        }
                        repeat(3 - rowMonths.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    if (hebrewUi) "היום" else "Today",
                    color = ACTIVE_GOLD, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(9.dp)).clickable { onToday() }
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** Round −/+ button that fires once on tap and repeats while held. */
@Composable
private fun YearArrow(glyph: String, onStep: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
            .pointerInput(onStep) {
                awaitEachGesture {
                    awaitFirstDown()
                    onStep()
                    var delayMs = 400L
                    while (true) {
                        val event = withTimeoutOrNull(delayMs) { awaitPointerEvent() }
                        if (event == null) { onStep(); delayMs = 80L }
                        else if (event.changes.all { !it.pressed }) break
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = INACTIVE, fontSize = 20.sp)
    }
}

@Composable
private fun YearField(initial: Int, range: IntRange, hebrewUi: Boolean, onDone: (Int) -> Unit) {
    var value by remember { mutableStateOf(initial.toString()) }
    val focus = remember { FocusRequester() }
    BasicTextField(
        value = value,
        onValueChange = { v -> value = v.filter { it.isDigit() }.take(4) },
        textStyle = TextStyle(
            color = ACTIVE_GOLD, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(ACTIVE_GOLD),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone(value.toIntOrNull()?.coerceIn(range) ?: initial) }),
        singleLine = true,
        modifier = Modifier.width(120.dp).focusRequester(focus)
    )
    LaunchedEffect(Unit) {
        withFrameMillis { }
        focus.requestFocus()
    }
}
