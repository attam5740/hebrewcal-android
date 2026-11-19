package com.hebrewcal.ui.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebrewcal.data.TorahNavigation
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// The reader's navigation dial: book / parsha / chapter picker, rotary dial and flame chips.

/** Ember palette for the animated flame chips: deep red → orange → gold and back. */
private val FLAME = listOf(
    Color(0xFFB3160B), Color(0xFFFF4D00), Color(0xFFFFA000), Color(0xFFFFE066),
    Color(0xFFFFA000), Color(0xFFFF4D00), Color(0xFFB3160B)
)
private val FLAME_INK = Color(0xFF1A0A00)

/** Shared flame animation: a linear sweep phase (0→1) and a breathing glow (0.5↔1). */
@Composable
private fun rememberFlame(): Pair<State<Float>, State<Float>> {
    val transition = rememberInfiniteTransition(label = "flame")
    val phase = transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "flamePhase"
    )
    val breath = transition.animateFloat(
        0.5f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flameBreath"
    )
    return phase to breath
}

/** Soft ember halo drawn in expanding translucent layers around the whole bounds. */
private fun DrawScope.emberGlow(breath: Float, cornerPx: Float) {
    for (i in 1..5) {
        val grow = i * 4.dp.toPx()
        drawRoundRect(
            color = Color(0xFFFF6A00).copy(alpha = 0.14f * breath * (6 - i) / 5f),
            topLeft = Offset(-grow, -grow),
            size = Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = CornerRadius(cornerPx + grow)
        )
    }
}

/** Navigation chip: quiet when inactive; the active one burns with a sweeping gradient and breathing glow. */
@Composable
private fun FlameToggle(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    if (!active) {
        Text(
            label, color = INACTIVE, fontSize = 15.sp,
            modifier = modifier.padding(horizontal = 3.dp).clip(shape).clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
        return
    }
    val (phase, breath) = rememberFlame()
    Text(
        label, color = FLAME_INK, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 3.dp)
            .drawBehind { emberGlow(breath.value, 14.dp.toPx()) }
            .clip(shape)
            .drawBehind {
                val w = size.width
                val shift = 2 * w * phase.value   // mirror tiling makes a 2w shift loop seamlessly
                drawRect(
                    Brush.linearGradient(
                        FLAME, start = Offset(-w + shift, 0f), end = Offset(shift, size.height), tileMode = TileMode.Mirror
                    )
                )
            }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/** The dial's "go" control: a 46dp ring of spinning fire with a bold ↵ so it reads as a button. */
@Composable
private fun FlameGoButton(onGo: () -> Unit) {
    val (phase, breath) = rememberFlame()
    Box(
        Modifier.padding(horizontal = 12.dp).size(46.dp)
            .drawBehind {
                val r = size.minDimension / 2f
                for (i in 1..5) drawCircle(
                    Color(0xFFFF6A00).copy(alpha = 0.14f * breath.value * (6 - i) / 5f),
                    radius = r + i * 4.dp.toPx()
                )
                rotate(phase.value * 360f) { drawCircle(Brush.sweepGradient(FLAME), radius = r) }
                drawCircle(Color(0xFFFFB300), radius = r - 5.dp.toPx())
            }
            .clip(CircleShape)
            .clickable { onGo() },
        contentAlignment = Alignment.Center
    ) {
        Text("↵", color = FLAME_INK, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Semi-transparent navigation overlay. Tehillim: a rotary dial over chapters
 * 1-150. Torah: pick a book, then dial a chapter or choose a parsha.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
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
                // The five books overflow narrow screens; keep the active one in view.
                val activeBook = remember { BringIntoViewRequester() }
                LaunchedEffect(bookIdx) { activeBook.bringIntoView() }
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TorahNavigation.BOOKS.forEachIndexed { i, b ->
                        FlameToggle(
                            if (hebrew) b.he else b.en, active = i == bookIdx,
                            modifier = if (i == bookIdx) Modifier.bringIntoViewRequester(activeBook) else Modifier
                        ) { bookIdx = i }
                    }
                }
                Row(Modifier.padding(top = 6.dp)) {
                    FlameToggle(if (hebrew) "פרשה" else "Parsha", active = byParsha) { byParsha = true }
                    FlameToggle(if (hebrew) "פרק" else "Chapter", active = !byParsha) { byParsha = false }
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
                    FlameGoButton { onGo(value) }
                    Text("+", color = INACTIVE, fontSize = 26.sp, modifier = Modifier.clickable { value = (value + 1).coerceIn(min, max) }.padding(horizontal = 14.dp))
                }
            }
        }
    }
}
