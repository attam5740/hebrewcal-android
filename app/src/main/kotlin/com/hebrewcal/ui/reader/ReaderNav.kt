package com.hebrewcal.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebrewcal.data.TorahNavigation
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// The reader's navigation dial: book / parsha / chapter picker, rotary dial and quiet chips.

/**
 * Largest font size in [maxSp] down to [minSp] (0.5sp steps) at which [widthAt] fits in
 * [availablePx], or null when even [minSp] does not fit.
 */
internal fun fitFontSp(availablePx: Float, widthAt: (Float) -> Float, maxSp: Float = 15f, minSp: Float = 11f): Float? {
    var sp = maxSp
    while (sp >= minSp) {
        if (widthAt(sp) <= availablePx) return sp
        sp -= 0.5f
    }
    return null
}

/** Quiet navigation chip: plain text when inactive; gold text on a faint gold wash with a hairline border when active. */
@Composable
private fun NavChip(
    label: String, active: Boolean, modifier: Modifier = Modifier, fontSize: TextUnit = 15.sp,
    horizontalPadding: Dp = 12.dp, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        label,
        color = if (active) ACTIVE_GOLD else INACTIVE,
        fontSize = fontSize, fontWeight = FontWeight.Medium, maxLines = 1, textAlign = TextAlign.Center,
        modifier = modifier.clip(shape)
            .then(
                if (active) Modifier.background(ACTIVE_GOLD.copy(alpha = 0.16f))
                    .border(1.dp, ACTIVE_GOLD.copy(alpha = 0.5f), shape)
                else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = horizontalPadding, vertical = 7.dp)
    )
}

/**
 * The five books filling the available width: equal-weight chips on one row with the largest
 * font (15sp down to 11sp) that fits the longest label; if even 11sp does not fit, 3 + 2 rows.
 */
@Composable
private fun BooksRow(hebrew: Boolean, selected: Int, onSelect: (Int) -> Unit) {
    val labels = TorahNavigation.BOOKS.map { if (hebrew) it.he else it.en }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val chipPad = 4.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val totalPx = with(density) { maxWidth.toPx() }
        val padPx = with(density) { (chipPad * 2).toPx() } + 2f // 2px slack for the 1dp border rounding
        fun widthAt(sp: Float) = labels.maxOf {
            measurer.measure(it, TextStyle(fontSize = sp.sp, fontWeight = FontWeight.Medium), maxLines = 1).size.width
        }.toFloat() + padPx
        val oneRowSp = fitFontSp(totalPx / 5f, ::widthAt)
        val twoRows = oneRowSp == null
        val sp = (oneRowSp ?: fitFontSp(totalPx / 3f, ::widthAt) ?: 11f).sp
        @Composable
        fun chip(i: Int, m: Modifier) =
            NavChip(labels[i], i == selected, m, sp, chipPad) { onSelect(i) }
        if (!twoRows) {
            Row(Modifier.fillMaxWidth()) { for (i in labels.indices) chip(i, Modifier.weight(1f)) }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) { for (i in 0..2) chip(i, Modifier.weight(1f)) }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) { for (i in 3..4) chip(i, Modifier.weight(1f)) }
            }
        }
    }
}

/** The dial's "go" control: a plain 46dp gold-outlined circle with a gold return arrow. */
@Composable
private fun NavGoButton(onGo: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 12.dp).size(46.dp)
            .clip(CircleShape)
            .border(1.5.dp, ACTIVE_GOLD, CircleShape)
            .clickable { onGo() },
        contentAlignment = Alignment.Center
    ) {
        Text("↵", color = ACTIVE_GOLD, fontSize = 24.sp)
    }
}

/**
 * Semi-transparent navigation overlay. Tehillim: a rotary dial over chapters
 * 1-150. Torah: pick a book, then dial a chapter or choose a parsha.
 */
@Composable
internal fun NavDialOverlay(
    mode: String,
    hebrew: Boolean,
    parshiyot: Map<String, List<TorahNavigation.Parsha>>,
    ensureParshiyot: (String) -> Unit,
    onPick: (mode: String, ref: String, title: String, aliyot: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val gematria = remember { HebrewDateFormatter() }
    fun num(n: Int) = if (hebrew) gematria.formatHebrewNumber(n) else n.toString()
    val torah = mode == "parsha"
    var bookIdx by rememberSaveable { mutableStateOf(4) }
    var byParsha by rememberSaveable { mutableStateOf(true) }
    val book = TorahNavigation.BOOKS[bookIdx]
    if (torah && byParsha) LaunchedEffect(book.en) { ensureParshiyot(book.en) }

    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides if (hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr) { Column(
            Modifier.fillMaxWidth(0.9f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (torah) {
                BooksRow(hebrew, bookIdx) { bookIdx = it }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NavChip(if (hebrew) "פרשה" else "Parsha", active = byParsha) { byParsha = true }
                    NavChip(if (hebrew) "פרק" else "Chapter", active = !byParsha) { byParsha = false }
                }
                Spacer(Modifier.height(10.dp))
                if (byParsha) {
                    val list = parshiyot[book.en]
                    if (list == null) {
                        CircularProgressIndicator(color = ACTIVE_GOLD, modifier = Modifier.padding(24.dp))
                    } else {
                        LazyColumn(Modifier.fillMaxHeight(0.55f).fillMaxWidth()) {
                            items(list) { pr ->
                                Text(
                                    if (hebrew) pr.he else pr.en,
                                    color = INACTIVE, fontSize = 17.sp,
                                    textAlign = TextAlign.Start, // layout direction already follows the language
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { onPick("parsha", pr.dottedRef, if (hebrew) "פרשת ${pr.he}" else "Parashat ${pr.en}", pr.aliyot) }
                                        .padding(vertical = 9.dp, horizontal = 12.dp)
                                )
                            }
                        }
                    }
                } else {
                    RotaryDial(min = 1, max = book.chapters, label = { num(it) }) { ch ->
                        onPick("parsha", "${book.en}.$ch", if (hebrew) "${book.he} פרק ${num(ch)}" else "${book.en} $ch", emptyList())
                    }
                }
            } else {
                Text(if (hebrew) "תהילים" else "Tehillim", color = ACTIVE_GOLD, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                RotaryDial(min = 1, max = TorahNavigation.TEHILLIM_CHAPTERS, label = { num(it) }) { ch ->
                    onPick("tehillim", "Psalms.$ch", if (hebrew) "תהילים פרק ${num(ch)}" else "Psalm $ch", emptyList())
                }
            }
        } }
    }
}

/**
 * A rotary dial: drag around the ring to turn (steps per turn scale with the range), tap
 * the center to jump. Tiny ± nudges for precision.
 */
@Composable
private fun RotaryDial(min: Int, max: Int, label: (Int) -> String, onGo: (Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf(min) }
    var acc by remember { mutableStateOf(0f) }
    var lastAngle by remember { mutableStateOf<Float?>(null) }
    val stepsPerTurn = TorahNavigation.dialStepsPerTurn(max - min + 1).toFloat()
    val ringColor = Color.White.copy(alpha = 0.35f)

    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(min, max) {
                detectDragGestures(
                    onDragStart = { lastAngle = null },
                    onDragEnd = { lastAngle = null }
                ) { change, _ ->
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val a = atan2(change.position.y - cy, change.position.x - cx)
                    lastAngle?.let { prev ->
                        var d = a - prev
                        if (d > PI) d -= (2 * PI).toFloat()
                        if (d < -PI) d += (2 * PI).toFloat()
                        acc += d
                        val steps = (acc / (2 * PI).toFloat() * stepsPerTurn).toInt()
                        if (steps != 0) {
                            value = (value + steps).coerceIn(min, max)
                            acc -= steps / stepsPerTurn * (2 * PI).toFloat()
                        }
                    }
                    lastAngle = a
                    change.consume()
                }
            }
        ) {
            val r = size.minDimension / 2f - 14.dp.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(ringColor, radius = r, center = c, style = Stroke(width = 10.dp.toPx()))
            // progress arc through the range
            val sweep = 360f * (value - min).toFloat() / (max - min).coerceAtLeast(1)
            drawArc(
                ACTIVE_GOLD, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                style = Stroke(width = 10.dp.toPx())
            )
            // ticks
            for (i in 0 until 24) {
                val ang = (i / 24f) * 2f * PI.toFloat()
                val inner = r - 16.dp.toPx(); val outer = r - 9.dp.toPx()
                drawLine(
                    Color.White.copy(alpha = 0.25f),
                    Offset(c.x + inner * cos(ang), c.y + inner * sin(ang)),
                    Offset(c.x + outer * cos(ang), c.y + outer * sin(ang)),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label(value), color = Color.White, fontSize = 40.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { onGo(value) }
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("−", color = INACTIVE, fontSize = 26.sp, modifier = Modifier.clickable { value = (value - 1).coerceIn(min, max) }.padding(horizontal = 14.dp))
                    NavGoButton { onGo(value) }
                    Text("+", color = INACTIVE, fontSize = 26.sp, modifier = Modifier.clickable { value = (value + 1).coerceIn(min, max) }.padding(horizontal = 14.dp))
                }
            }
        }
    }
}
