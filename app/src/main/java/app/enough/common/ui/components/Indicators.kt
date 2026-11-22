package app.enough.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors

@Composable
fun BlockBar(fraction: Float, red: Boolean = false, segments: Int = 20, modifier: Modifier = Modifier) {
    val c = LocalEnoughColors.current
    val col = if (red) c.red else c.fg
    val f = fraction.coerceIn(0f, 1f)
    val filled = (f * segments).toInt()
    Row(
        modifier.fillMaxWidth().semantics { progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f) },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(segments) { i ->
            Box(
                Modifier.weight(1f).height(10.dp).border(1.dp, col)
                    .background(if (i < filled) col else Color.Transparent),
            )
        }
    }
}

/** Padlock glyph (filled body + shackle), drawn so it matches the hard-edged style. Announces "Locked". */
@Composable
fun LockIcon(tint: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size).semantics { contentDescription = "Locked" }) {
        val u = this.size.minDimension / 24f
        val sw = 2.6f * u
        drawRect(tint, topLeft = androidx.compose.ui.geometry.Offset(4f * u, 11f * u), size = androidx.compose.ui.geometry.Size(16f * u, 11f * u))
        val shackle = androidx.compose.ui.graphics.Path().apply {
            moveTo(7.5f * u, 11f * u)
            lineTo(7.5f * u, 8f * u)
            arcTo(androidx.compose.ui.geometry.Rect(7.5f * u, 3.5f * u, 16.5f * u, 12.5f * u), 180f, 180f, false)
            lineTo(16.5f * u, 11f * u)
        }
        drawPath(shackle, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
    }
}

/** Rounded circular progress with the percentage inside (download progress). */
@Composable
fun ProgressRing(percent: Int, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val c = LocalEnoughColors.current
    val pct = percent.coerceIn(0, 100)
    val sweep by androidx.compose.animation.core.animateFloatAsState(pct * 3.6f, label = "ring")
    Box(
        modifier.size(size).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(pct / 100f, 0f..1f)
            contentDescription = "Downloading $pct percent"
        },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val sw = 5.dp.toPx()
            val inset = sw / 2f
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(c.line, 0f, 360f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            drawArc(
                c.fg, -90f, sweep, false, topLeft, arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        Txt("$pct%", EnoughType.num.copy(fontSize = 13.sp), c.fg)
    }
}
