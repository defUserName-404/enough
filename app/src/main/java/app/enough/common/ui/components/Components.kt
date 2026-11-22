package app.enough.common.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.common.domain.durMin
import app.enough.common.ui.S
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import app.enough.limits.domain.Limits

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    color: Color = LocalEnoughColors.current.fg,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(text, modifier, style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Wraps [onClick] with a short haptic tick (respects the system "touch feedback" setting). */
@Composable
fun hapticClick(kind: Int = HapticFeedbackConstants.VIRTUAL_KEY, onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return { view.performHapticFeedback(kind); onClick() }
}

/** Clickable without ripple/indication, with haptics. */
@Composable
fun Modifier.plainClickable(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val click = hapticClick(onClick = onClick)
    return clickable(interactionSource = src, indication = null, enabled = enabled, role = role, onClick = click)
}

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

@Composable
fun EnoughCheckBox(checked: Boolean, modifier: Modifier = Modifier) {
    val c = LocalEnoughColors.current
    Box(
        modifier.size(24.dp).border(2.dp, c.fg).background(if (checked) c.fg else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Txt("✓", EnoughType.label.copy(fontSize = 14.sp), c.bg)
    }
}

@Composable
fun EnoughRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalEnoughColors.current
    val base = if (onClick != null) modifier.plainClickable(role = Role.Button, onClick = onClick) else modifier
    Column(base.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun EnoughBox(red: Boolean = false, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalEnoughColors.current
    Column(modifier.fillMaxWidth().border(2.dp, if (red) c.red else c.fg).padding(16.dp), content = content)
}

@Composable
fun Hairline(thick: Boolean = false) {
    val c = LocalEnoughColors.current
    Box(
        Modifier.fillMaxWidth().height(if (thick) 2.dp else 1.dp).background(if (thick) c.fg else c.line),
    )
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = LocalEnoughColors.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = EnoughType.num.copy(color = c.fg, textAlign = TextAlign.Start),
        cursorBrush = SolidColor(c.fg),
        modifier = Modifier.fillMaxWidth().height(52.dp).border(2.dp, c.fg).padding(horizontal = 14.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Txt(S.SEARCH, EnoughType.num, c.dim)
                inner()
            }
        },
    )
}

@Composable
fun Scrim(onDismiss: () -> Unit) {
    val c = LocalEnoughColors.current
    Box(Modifier.fillMaxSize().background(c.scrim).plainClickable(onClick = onDismiss))
}

/** Two-or-more cell tab switcher; the selected cell is filled. */
@Composable
fun EnoughTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalEnoughColors.current
    val tick = hapticClick(onClick = {})
    Row(modifier.fillMaxWidth().border(2.dp, c.fg)) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).background(if (sel) c.fg else Color.Transparent)
                    .plainClickable(role = Role.Tab) { tick(); onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Txt(label, EnoughType.label.copy(fontSize = 12.sp), if (sel) c.bg else c.fg, Modifier.padding(8.dp))
            }
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
