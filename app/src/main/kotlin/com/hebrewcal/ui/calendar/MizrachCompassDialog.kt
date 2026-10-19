package com.hebrewcal.ui.calendar

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hebrewcal.data.MizrachCalculator
import com.hebrewcal.data.MizrachResult
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MizrachCompassDialog(
    latitude: Double,
    longitude: Double,
    isHebrew: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var deviceHeading by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }
    var lastVibratedAlignment by remember { mutableStateOf(false) }

    // Sensor registration
    DisposableEffect(latitude, longitude) {
        // The sensors report heading from magnetic north, but the bearing to Jerusalem is from
        // true north. The local declination (e.g. about -10° in Ontario) bridges the two;
        // without it the needle is off by that much and lights up at the wrong spot.
        val declination = GeomagneticField(
            latitude.toFloat(), longitude.toFloat(), 0f, System.currentTimeMillis()
        ).declination.toDouble()
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        if (rotationSensor == null) {
            hasSensor = false
        } else {
            val listener = object : SensorEventListener {
                private val rotationMatrix = FloatArray(9)
                private val orientation = FloatArray(3)
                // Low-pass filter on the unit circle (sin/cos), so noise near 0°/360° averages
                // out instead of flipping between 359° and 1°.
                private var sx = 0.0
                private var cy = 0.0
                private var primed = false

                private fun smooth(deg: Double) {
                    val r = Math.toRadians(deg)
                    if (!primed) { sx = Math.sin(r); cy = Math.cos(r); primed = true }
                    else { sx += 0.15 * (Math.sin(r) - sx); cy += 0.15 * (Math.cos(r) - cy) }
                    deviceHeading = ((Math.toDegrees(Math.atan2(sx, cy)) + 360.0) % 360.0).toFloat()
                }

                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        val azimuthRad = orientation[0]
                        val azimuthDeg = (Math.toDegrees(azimuthRad.toDouble()) + 360.0) % 360.0
                        smooth(azimuthDeg + declination)
                    } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
                        smooth(event.values[0].toDouble() + declination)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }

        onDispose { }
    }

    val mizrachResult = remember(latitude, longitude, deviceHeading, isHebrew) {
        MizrachCalculator.calculate(
            fromLat = latitude,
            fromLng = longitude,
            currentDeviceHeading = deviceHeading,
            alignmentThresholdDegrees = 5.0f,
            isHebrew = isHebrew
        )
    }

    // Alignment with hysteresis: it turns on within 5° but only turns off past 9°, so a heading
    // hovering at the threshold no longer toggles the dial (and the haptic) on and off.
    val offTarget = abs(((mizrachResult.bearingDegrees - deviceHeading) % 360f + 540f) % 360f - 180f)
    var aligned by remember { mutableStateOf(false) }
    if (!aligned && offTarget <= 5f) aligned = true
    else if (aligned && offTarget > 9f) aligned = false
    // 0..1, eased, so the ring and needle glow fade rather than flash.
    val glow by animateFloatAsState(if (aligned) 1f else 0f, tween(400), label = "align_glow")

    // Trigger gentle haptic vibration upon snapping into alignment
    LaunchedEffect(aligned) {
        if (aligned && !lastVibratedAlignment) {
            // View haptics need no VIBRATE permission (the Vibrator API does, and the app declares none).
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            lastVibratedAlignment = true
        } else if (!aligned) {
            lastVibratedAlignment = false
        }
    }

    // Pointer rotation relative to the phone's top
    val relativeJerusalemAngle = (mizrachResult.bearingDegrees - deviceHeading + 360f) % 360f
    // Unwrapped angle: always move by the shortest delta, so crossing 0°/360° never makes the
    // needle spin the long way round.
    var continuousAngle by remember { mutableFloatStateOf(relativeJerusalemAngle) }
    val delta = ((relativeJerusalemAngle - continuousAngle) % 360f + 540f) % 360f - 180f
    if (delta != 0f) continuousAngle += delta
    val animatedAngle by animateFloatAsState(
        targetValue = continuousAngle,
        animationSpec = tween(200),
        label = "compass_rotation"
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1A16))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isHebrew) "כיוון התפילה לירושלים (מזרח)" else "Mizrach (Jerusalem Direction)",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Compass Dial Canvas
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF12100E)),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Ring & Cardinal Ticks
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.width / 2f - 12f

                        // Dial circle
                        drawCircle(
                            color = lerp(Color(0x33B0A898), GOLD_PRIMARY, glow),
                            radius = radius,
                            style = Stroke(width = (1.5f + 2f * glow).dp.toPx())
                        )

                        // 4 cardinal tick marks
                        for (i in 0 until 4) {
                            val angleRad = Math.toRadians((i * 90.0) - 90.0)
                            val inner = Offset(
                                (center.x + (radius - 12f) * cos(angleRad)).toFloat(),
                                (center.y + (radius - 12f) * sin(angleRad)).toFloat()
                            )
                            val outer = Offset(
                                (center.x + radius * cos(angleRad)).toFloat(),
                                (center.y + radius * sin(angleRad)).toFloat()
                            )
                            drawLine(
                                color = if (i == 0) Color(0xFFEF5350) else Color(0xFFB0A898),
                                start = inner,
                                end = outer,
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }

                    // Rotating Arrow pointing to Jerusalem
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(animatedAngle),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val arrowLen = size.width / 2f - 24f

                            // Upward golden pointer arrow
                            val arrowPath = Path().apply {
                                moveTo(cx, cy - arrowLen) // top tip
                                lineTo(cx - 16f, cy)
                                lineTo(cx + 16f, cy)
                                close()
                            }
                            drawPath(
                                path = arrowPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        lerp(GOLD_PRIMARY, GOLD_GLOW, glow),
                                        Color(0xFF8C6D1F)
                                    )
                                )
                            )

                            // Center pivot pin
                            drawCircle(
                                color = Color.White,
                                radius = 5.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }

                // Alignment status text: a fixed-height slot, cross-faded, so the dialog never resizes
                Crossfade(aligned, Modifier.height(22.dp), tween(400), label = "align_text") { isAligned ->
                if (isAligned) {
                    Text(
                        text = if (isHebrew) "✦ פונה בדיוק לירושלים ✦" else "✦ Aligned with Jerusalem ✦",
                        color = GOLD_PRIMARY,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = if (isHebrew) {
                            "כיוון: ${mizrachResult.cardinalDirection} (${mizrachResult.bearingDegrees.toInt()}°)"
                        } else {
                            "Bearing: ${mizrachResult.cardinalDirection} (${mizrachResult.bearingDegrees.toInt()}°)"
                        },
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
                }

                // Distance to Temple Mount
                Text(
                    text = if (isHebrew) {
                        "${mizrachResult.distanceKm.toInt()} ק\"מ להר הבית, ירושלים"
                    } else {
                        "${mizrachResult.distanceMiles.toInt()} mi (${mizrachResult.distanceKm.toInt()} km) to Temple Mount"
                    },
                    color = PARCHMENT_DIM,
                    fontSize = 12.sp
                )
            }
        }
    }
}
