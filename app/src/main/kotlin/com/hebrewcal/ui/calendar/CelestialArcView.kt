package com.hebrewcal.ui.calendar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebrewcal.data.BirkatHaLevanaInfo
import com.hebrewcal.data.CelestialArcData
import com.hebrewcal.data.MoonPhaseInfo
import java.util.Date
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

val GOLD_PRIMARY = Color(0xFFD4AF37)
val GOLD_GLOW = Color(0xFFFFE082)
val PARCHMENT_DIM = Color(0xFFB0A898)
val SKY_DARK = Color(0x33221C14)
val MOON_LIGHT = Color(0xFFF2ECD8)

@Composable
fun CelestialArcView(
    arcData: CelestialArcData,
    moonInfo: MoonPhaseInfo,
    birkatHaLevana: BirkatHaLevanaInfo,
    isHebrew: Boolean,
    southern: Boolean = false,
    formatTime: (Date) -> String,
    onOpenCompass: () -> Unit,
    onOpenBirkatHaLevana: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sun_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SKY_DARK)
            .padding(14.dp)
    ) {
        // Header: Shaot Zmaniyot & Compass Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isHebrew) "קשת השמש ושעות זמניות" else "Celestial Sun Arc & Shaot Zmaniyot",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = arcData.formattedShaah,
                    color = GOLD_PRIMARY,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onOpenCompass) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = if (isHebrew) "מצפן ירושלים" else "Jerusalem Compass",
                    tint = GOLD_PRIMARY
                )
            }
        }

        // The arc and sun only mean something on today; other days keep just the times below.
        if (arcData.isLive) {
        Spacer(modifier = Modifier.height(8.dp))

        // Visual Celestial Arc Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val startX = 20f
                val endX = w - 20f
                val baseY = h - 16f
                val arcHeight = h - 28f

                // Draw horizon base line
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(startX, baseY),
                    end = Offset(endX, baseY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Draw celestial daylight dome path
                val path = Path().apply {
                    moveTo(startX, baseY)
                    // Quadratic curve over the sky dome
                    quadraticBezierTo(w / 2f, baseY - arcHeight * 2f, endX, baseY)
                }

                drawPath(
                    path = path,
                    color = GOLD_PRIMARY.copy(alpha = 0.35f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                val ctrlX = w / 2f
                val ctrlY = baseY - arcHeight * 2f
                // Facing the sun at midday (south, from the northern hemisphere), east is on the
                // right: the sun rises at the right end of the arc and sets at the left.
                val riseX = endX
                val setX = startX
                // Quadratic bezier: B(t) = (1-t)^2 P0 + 2(1-t)t P1 + t^2 P2, t = 0 at sunrise, 1 at sunset.
                fun pointAt(t: Float) = Offset(
                    (1 - t) * (1 - t) * riseX + 2 * (1 - t) * t * ctrlX + t * t * setX,
                    (1 - t) * (1 - t) * baseY + 2 * (1 - t) * t * ctrlY + t * t * baseY
                )

                if (arcData.isDaytime) {
                    val t = arcData.dayProgress.coerceIn(0f, 1f)
                    val sun = pointAt(t)

                    // The way the sun has come today: the arc from sunrise up to it, drawn bright.
                    val trail = Path().apply {
                        moveTo(riseX, baseY)
                        val steps = 40
                        for (i in 1..steps) pointAt(t * i / steps).let { lineTo(it.x, it.y) }
                    }
                    drawPath(trail, GOLD_PRIMARY.copy(alpha = 0.9f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

                    // Arrowhead just ahead of the sun, pointing along its path (B'(t)).
                    if (t < 0.93f) {
                        val dx = 2 * (1 - t) * (ctrlX - riseX) + 2 * t * (setX - ctrlX)
                        val dy = 2 * (1 - t) * (ctrlY - baseY) + 2 * t * (baseY - ctrlY)
                        val len = sqrt(dx * dx + dy * dy)
                        if (len > 0f) {
                            val ux = dx / len
                            val uy = dy / len
                            val tip = Offset(sun.x + ux * 20.dp.toPx(), sun.y + uy * 20.dp.toPx())
                            val back = Offset(tip.x - ux * 6.dp.toPx(), tip.y - uy * 6.dp.toPx())
                            val half = 3.5.dp.toPx()
                            val arrow = Path().apply {
                                moveTo(tip.x, tip.y)
                                lineTo(back.x - uy * half, back.y + ux * half)
                                lineTo(back.x + uy * half, back.y - ux * half)
                                close()
                            }
                            drawPath(arrow, GOLD_GLOW.copy(alpha = 0.85f))
                        }
                    }

                    // Sun glow ring
                    drawCircle(
                        color = GOLD_GLOW.copy(alpha = pulseAlpha * 0.4f),
                        radius = 12.dp.toPx(),
                        center = sun
                    )
                    // Sun orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, GOLD_PRIMARY),
                            center = sun,
                            radius = 6.dp.toPx()
                        ),
                        radius = 6.dp.toPx(),
                        center = sun
                    )
                } else if (arcData.isLive) {
                    // Tonight: the moon in its current phase, under the dome of the day's arc.
                    drawMoonPhase(Offset(w / 2f, baseY - arcHeight * 0.55f), 8.dp.toPx(), moonInfo.phaseFraction, southern)
                }
            }
        }
        }

        // West/sunset under the left end of the arc, East/sunrise under the right, and (today,
        // by day) the time left to sunset between them. Pinned left-to-right so a Hebrew
        // layout does not swap the ends away from the arc they label.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sunrise = arcData.sunrise?.let(formatTime) ?: ""
                val sunset = arcData.sunset?.let(formatTime) ?: ""
                Text(
                    text = if (isHebrew) "מערב · שקיעה $sunset" else "West · sunset $sunset",
                    color = PARCHMENT_DIM, fontSize = 10.sp, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Left
                )
                countdown(arcData, isHebrew)?.let {
                    Text(it, color = GOLD_PRIMARY, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = if (isHebrew) "מזרח · הנץ $sunrise" else "East · sunrise $sunrise",
                    color = PARCHMENT_DIM, fontSize = 10.sp, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Right
                )
            }
        }

        // Sub-bar: Moon phase & Birkat HaLevana badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(12.dp)) {
                    drawMoonPhase(center, size.minDimension / 2f, moonInfo.phaseFraction, southern)
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "${moonInfo.phaseName} (${moonInfo.illuminationPercent}%)",
                    color = PARCHMENT_DIM,
                    fontSize = 11.sp
                )
            }

            if (birkatHaLevana.isOpenNow) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(GOLD_PRIMARY.copy(alpha = 0.25f))
                        .clickable { onOpenBirkatHaLevana() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isHebrew) "🌕 קדש לבנה ›" else "🌕 Birkat HaLevana Open ›",
                        color = GOLD_PRIMARY,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (birkatHaLevana.statusText.isNotEmpty()) {
                Text(
                    text = birkatHaLevana.statusText,
                    color = PARCHMENT_DIM,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * The moon as seen in the sky: a dim disc with its lit part. [phaseFraction] is 0 at new moon
 * and 0.5 at full. North of the equator a waxing moon is lit on the right; [southern] mirrors it.
 */
private fun DrawScope.drawMoonPhase(center: Offset, radius: Float, phaseFraction: Float, southern: Boolean) {
    drawCircle(Color.White.copy(alpha = 0.15f), radius, center)
    val lit = ((1 - cos(2 * PI * phaseFraction)) / 2).toFloat()
    if (lit < 0.01f) return
    // The terminator is a half-ellipse; its width shrinks to nothing at half-lit.
    val a = maxOf(radius * abs(1 - 2 * lit), 0.5f)
    val path = Path().apply {
        // Lit limb: the right half of the disc, top to bottom.
        arcTo(Rect(center, radius), -90f, 180f, forceMoveTo = true)
        // Terminator back to the top: bulging toward the limb for a crescent, away past half.
        arcTo(
            Rect(center.x - a, center.y - radius, center.x + a, center.y + radius),
            90f, if (lit < 0.5f) -180f else 180f, forceMoveTo = false
        )
        close()
    }
    val litOnRight = (phaseFraction < 0.5f) != southern
    scale(if (litOnRight) 1f else -1f, 1f, pivot = center) { drawPath(path, MOON_LIGHT) }
}

/** "3h 12m to sunset"; null outside today's daylight. */
private fun countdown(arc: CelestialArcData, isHebrew: Boolean): String? {
    if (!arc.isDaytime) return null
    val sunset = arc.sunset ?: return null
    val mins = ((sunset.time - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
    return if (isHebrew) "עוד %d:%02d לשקיעה".format(mins / 60, mins % 60)
        else "%dh %02dm to sunset".format(mins / 60, mins % 60)
}
