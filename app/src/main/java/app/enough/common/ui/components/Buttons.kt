package app.enough.common.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.common.domain.durMin
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import app.enough.limits.domain.Limits

enum class BtnStyle { OUTLINE, PRIMARY, DANGER, GHOST }

@Composable
fun EnoughButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.OUTLINE,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    val c = LocalEnoughColors.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val eff = if (enabled) style else BtnStyle.GHOST
    val border: Color
    val txt: Color
    val fill: Color
    when (eff) {
        BtnStyle.OUTLINE -> { border = c.fg; txt = c.fg; fill = Color.Transparent }
        BtnStyle.PRIMARY -> { border = c.fg; txt = c.bg; fill = c.fg }
        BtnStyle.DANGER -> { border = c.red; txt = c.red; fill = Color.Transparent }
        BtnStyle.GHOST -> { border = c.dim; txt = c.dim; fill = Color.Transparent }
    }
    val inv = pressed && enabled
    val click = hapticClick(
        if (style == BtnStyle.PRIMARY || style == BtnStyle.DANGER) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        onClick,
    )
    val bgNow = if (inv) txt else fill
    val txtNow = if (inv) (if (fill == Color.Transparent) c.bg else border) else txt
    Box(
        modifier
            .heightIn(min = if (small) 48.dp else 56.dp)
            .border(2.dp, border)
            .background(bgNow)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = click)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(
            text.uppercase(),
            if (small) EnoughType.button.copy(fontSize = 12.sp, letterSpacing = 1.4.sp) else EnoughType.button,
            txtNow,
            maxLines = 1,
        )
    }
}

/** Box that inverts while pressed. [filled] = starts inverted. Content receives its text color. */
@Composable
fun InvertBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    desc: String? = null,
    content: @Composable BoxScope.(Color) -> Unit,
) {
    val c = LocalEnoughColors.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val inv = (pressed && enabled) != filled
    val click = hapticClick(HapticFeedbackConstants.CLOCK_TICK, onClick)
    Box(
        modifier
            .background(if (inv) c.fg else Color.Transparent)
            .then(if (desc != null) Modifier.semantics { contentDescription = desc } else Modifier)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = click),
        contentAlignment = Alignment.Center,
    ) {
        content(if (inv) c.bg else if (enabled) c.fg else c.dim)
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, large: Boolean = false, modifier: Modifier = Modifier) {
    val c = LocalEnoughColors.current
    val canDec = value > Limits.MIN
    val canInc = value < Limits.MAX
    if (large) {
        Row(modifier.fillMaxWidth().height(120.dp).border(2.dp, c.fg)) {
            InvertBox({ onChange(value - Limits.STEP) }, Modifier.width(76.dp).fillMaxHeight(), canDec, desc = "Decrease limit") {
                Txt("–", EnoughType.big.copy(fontSize = 30.sp), it)
            }
            Box(Modifier.width(2.dp).fillMaxHeight().background(c.fg))
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Txt(durMin(value), EnoughType.big.copy(fontSize = 52.sp), c.fg)
            }
            Box(Modifier.width(2.dp).fillMaxHeight().background(c.fg))
            InvertBox({ onChange(value + Limits.STEP) }, Modifier.width(76.dp).fillMaxHeight(), canInc, filled = true, desc = "Increase limit") {
                Txt("+", EnoughType.big.copy(fontSize = 30.sp), it)
            }
        }
    } else {
        Row(modifier.height(48.dp).border(2.dp, c.fg)) {
            InvertBox({ onChange(value - Limits.STEP) }, Modifier.width(48.dp).fillMaxHeight(), canDec, desc = "Decrease limit") {
                Txt("–", EnoughType.num, it)
            }
            Box(Modifier.widthIn(min = 40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Txt(durMin(value), EnoughType.num, c.fg)
            }
            InvertBox({ onChange(value + Limits.STEP) }, Modifier.width(48.dp).fillMaxHeight(), canInc, desc = "Increase limit") {
                Txt("+", EnoughType.num, it)
            }
        }
    }
}
