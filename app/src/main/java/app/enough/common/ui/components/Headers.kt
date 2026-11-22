package app.enough.common.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.common.ui.S
import app.enough.common.ui.components.InvertBox
import app.enough.common.ui.components.Txt
import app.enough.common.ui.theme.Dims
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import kotlin.math.cos
import kotlin.math.sin

/** Outlined back button + big title; shared by Settings and Usage. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    val c = LocalEnoughColors.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        InvertBox({ onBack() }, Modifier.size(Dims.Touch).border(2.dp, c.fg), desc = S.BACK) {
            Txt("←", EnoughType.button.copy(fontSize = 20.sp), it)
        }
        Txt(title, EnoughType.h1.copy(fontSize = 30.sp, lineHeight = 30.sp), c.fg, Modifier.padding(start = 14.dp))
    }
}

/** Filled gear button for the Home header. */
@Composable
fun GearButton(onClick: () -> Unit) {
    InvertBox(onClick, Modifier.size(Dims.Touch), filled = true, desc = S.SETTINGS_DESC) { tint ->
        Canvas(Modifier.size(24.dp)) { drawGear(tint) }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGear(tint: Color) {
    val c = center
    val r = size.minDimension / 2f
    val sw = r * 0.2f
    drawCircle(tint, radius = r * 0.5f, center = c, style = Stroke(sw))
    drawCircle(tint, radius = r * 0.16f, center = c, style = Stroke(sw * 0.8f))
    for (i in 0 until 8) {
        val a = Math.PI * 2 * i / 8
        val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
        drawLine(
            tint, c + Offset(dx, dy) * (r * 0.62f), c + Offset(dx, dy) * (r * 0.98f),
            strokeWidth = sw * 1.5f, cap = StrokeCap.Butt,
        )
    }
}
