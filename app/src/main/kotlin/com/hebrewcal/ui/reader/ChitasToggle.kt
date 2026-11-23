package com.hebrewcal.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** The three parts of a Chitas day, in the order of the legs the reader loads. */
internal enum class ChitasPart(val labelEn: String, val labelHe: String) {
    TEHILIM("Tehilim", "תהילים"),
    CHUMASH("Chumash", "חומש"),
    TANYA("Tanya", "תניא")
}

/**
 * Full-width three-segment switch under the reader bar: Tehilim · Chumash · Tanya, each a glyph
 * followed by its label. The selected segment gets the gold wash and border of the navigator's
 * chips; the others are dimmed. In Hebrew the row lays out right to left, so Tehilim sits at the
 * right and each glyph precedes (is to the right of) its label.
 */
@Composable
internal fun ChitasToggle(selected: Int, hebrew: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val direction = if (hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        val shape = RoundedCornerShape(50)
        Row(
            modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ChitasPart.entries.forEachIndexed { i, part ->
                val on = i == selected
                val label = if (hebrew) part.labelHe else part.labelEn
                val ink = if (on) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.5f)
                val segment = RoundedCornerShape(50)
                Row(
                    Modifier.weight(1f)
                        .clip(segment)
                        .background(if (on) ACTIVE_GOLD.copy(alpha = 0.2f) else Color.Transparent)
                        .then(if (on) Modifier.border(1.dp, ACTIVE_GOLD.copy(alpha = 0.6f), segment) else Modifier)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                        .semantics { role = Role.Tab; this.selected = on; contentDescription = part.labelEn }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(Modifier.size(24.dp)) {
                        val c = Offset(size.width / 2, size.height / 2)
                        when (part) {
                            ChitasPart.TEHILIM -> drawHarp(c, ink)
                            ChitasPart.CHUMASH -> drawScroll(c, ink)
                            ChitasPart.TANYA -> drawMenorah(c, ink)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label, color = if (on) Color.White else ink, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

// Glyphs, in the line style of ModeToggle's harp and scroll: dp units around a centre point.

private fun DrawScope.drawHarp(c: Offset, color: Color) {
    drawArc(color, 200f, 200f, false, Offset(c.x - 5.dp.toPx(), c.y - 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()), style = Stroke(1.2.dp.toPx()))
    for (i in -1..1) drawLine(color, Offset(c.x + i * 2.5f.dp.toPx(), c.y - 3.dp.toPx()), Offset(c.x + i * 2.5f.dp.toPx(), c.y + 4.dp.toPx()), 1.dp.toPx())
}

private fun DrawScope.drawScroll(c: Offset, color: Color) {
    drawRoundRect(color, Offset(c.x - 5.dp.toPx(), c.y - 3.dp.toPx()), Size(10.dp.toPx(), 6.dp.toPx()), CornerRadius(1.dp.toPx()), style = Stroke(1.dp.toPx()))
    for (dx in listOf(-5f, 5f)) {
        drawLine(color, Offset(c.x + dx.dp.toPx(), c.y - 4.dp.toPx()), Offset(c.x + dx.dp.toPx(), c.y + 4.dp.toPx()), 1.6f.dp.toPx())
        for (dir in listOf(-1f, 1f)) drawLine(
            color, Offset(c.x + dx.dp.toPx(), c.y + dir * 4.dp.toPx()), Offset(c.x + dx.dp.toPx(), c.y + dir * 6.2f.dp.toPx()),
            0.9f.dp.toPx(), cap = StrokeCap.Round
        )
    }
}

/** Three nested chevrons (the Chabad menorah) under a single flame. */
private fun DrawScope.drawMenorah(c: Offset, color: Color) {
    val top = c.y - 0.4.dp.toPx()
    for (hw in listOf(7.2f, 4.8f, 2.4f)) {
        val p = Path().apply {
            moveTo(c.x - hw.dp.toPx(), top)
            lineTo(c.x, top + hw.dp.toPx())
            lineTo(c.x + hw.dp.toPx(), top)
        }
        drawPath(p, color, style = Stroke(1.25f.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round))
    }
    val fy = c.y - 4.4.dp.toPx()
    val flame = Path()
    for (deg in 0 until 360 step 12) {
        val a = Math.toRadians(deg.toDouble())
        val sn = sin(a).toFloat(); val cs = cos(a).toFloat()
        val x = c.x + 1.5f.dp.toPx() * sn * (0.35f + 0.65f * (1 - cs) / 2f) * 1.3f
        val y = fy - 2.2f.dp.toPx() * cs * (if (cs > 0) 1f else 0.7f)
        if (deg == 0) flame.moveTo(x, y) else flame.lineTo(x, y)
    }
    flame.close()
    drawPath(flame, color)
}
