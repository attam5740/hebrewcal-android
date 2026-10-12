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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebrewcal.data.BirkatHaLevanaInfo
import com.hebrewcal.data.CelestialArcData
import com.hebrewcal.data.MoonPhaseInfo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val GOLD_PRIMARY = Color(0xFFD4AF37)
val GOLD_GLOW = Color(0xFFFFE082)
val PARCHMENT_DIM = Color(0xFFB0A898)
val SKY_DARK = Color(0x33221C14)

@Composable
fun CelestialArcView(
    arcData: CelestialArcData,
    moonInfo: MoonPhaseInfo,
    birkatHaLevana: BirkatHaLevanaInfo,
    isHebrew: Boolean,
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

                // Calculate sun orb coordinates along the parabolic arc (progress 0.0 to 1.0)
                val t = arcData.dayProgress.coerceIn(0f, 1f)
                // Quadratic bezier formula: B(t) = (1-t)^2 P0 + 2(1-t)t P1 + t^2 P2
                val ctrlX = w / 2f
                val ctrlY = baseY - arcHeight * 2f

                val sunX = (1 - t) * (1 - t) * startX + 2 * (1 - t) * t * ctrlX + t * t * endX
                val sunY = (1 - t) * (1 - t) * baseY + 2 * (1 - t) * t * ctrlY + t * t * baseY

                if (arcData.isDaytime) {
                    // Sun glow ring
                    drawCircle(
                        color = GOLD_GLOW.copy(alpha = pulseAlpha * 0.4f),
                        radius = 12.dp.toPx(),
                        center = Offset(sunX, sunY)
                    )
                    // Sun orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, GOLD_PRIMARY),
                            center = Offset(sunX, sunY),
                            radius = 6.dp.toPx()
                        ),
                        radius = 6.dp.toPx(),
                        center = Offset(sunX, sunY)
                    )
                } else {
                    // Moon / Night indicator at bottom center
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = 5.dp.toPx(),
                        center = Offset(w / 2f, baseY + 6f)
                    )
                }
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
            Text(
                text = "${moonInfo.phaseName} (${moonInfo.illuminationPercent}%)",
                color = PARCHMENT_DIM,
                fontSize = 11.sp
            )

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
