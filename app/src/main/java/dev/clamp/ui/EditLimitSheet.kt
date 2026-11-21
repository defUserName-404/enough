package dev.clamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.Instant
import dev.clamp.core.Change
import dev.clamp.core.PendingChange
import dev.clamp.core.Target
import dev.clamp.core.durMin
import dev.clamp.core.left
import dev.clamp.ui.components.AppIcon
import dev.clamp.ui.components.BtnStyle
import dev.clamp.ui.components.ClampBox
import dev.clamp.ui.components.ClampButton
import dev.clamp.ui.components.Hairline
import dev.clamp.ui.components.InvertBox
import dev.clamp.ui.components.Scrim
import dev.clamp.ui.components.Stepper
import dev.clamp.ui.components.Txt
import dev.clamp.ui.theme.ClampType
import dev.clamp.ui.theme.Dims
import dev.clamp.ui.theme.LocalClampColors

@Composable
fun EditLimitSheet(
    target: Target,
    label: String,
    pending: PendingChange?,
    now: Instant,
    onSubmit: (Int) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    isNew: Boolean = false,
) {
    val c = LocalClampColors.current
    var value by remember(target.pkg, isNew) { mutableIntStateOf(target.limitMin) }
    val raising = !isNew && value > target.limitMin
    val same = !isNew && value == target.limitMin
    val pendingRemove = pending?.change is Change.Remove

    Box(Modifier.fillMaxSize()) {
        Scrim(onClose)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bg)) {
            Hairline(thick = true)
            Column(Modifier.padding(start = Dims.Pad, end = Dims.Pad, top = 22.dp, bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(target.pkg)
                    Txt(label.uppercase(), ClampType.h2, c.fg, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
                    InvertBox(onClose, Modifier.size(Dims.Touch), desc = "Close") { Txt("✕", ClampType.num, it) }
                }
                Txt("${S.DAILY_LIMIT} · NOW ${if (isNew) "NONE" else durMin(target.limitMin)}", ClampType.label, c.fg, Modifier.padding(top = 20.dp, bottom = 10.dp))
                Stepper(value, { value = it }, large = true)
                Box(Modifier.padding(top = 14.dp)) {
                    ClampBox(red = raising) {
                        // Headline only once the user has moved the stepper; the note explains both directions.
                        if (!same) Txt(
                            if (isNew) S.NEW_LIMIT else if (raising) S.RAISING else S.LOWERING,
                            ClampType.label.copy(fontSize = 12.sp), if (raising) c.red else c.fg,
                            Modifier.padding(bottom = 6.dp),
                        )
                        Txt(S.SHEET_NOTE, ClampType.body.copy(fontSize = 13.5.sp), c.fg)
                    }
                }
                if (pendingRemove && !same) {
                    Txt(
                        "THIS CANCELS THE PENDING REMOVE (${left(Duration.between(now, pending!!.applyAt))})",
                        ClampType.label, c.red, Modifier.padding(top = 10.dp),
                    )
                } else if (pending != null && !isNew) Txt(S.REPLACES_PENDING, ClampType.label, c.dim, Modifier.padding(top = 10.dp))
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isNew) {
                        ClampButton(S.REMOVE, onRemove, Modifier.weight(1.15f), BtnStyle.DANGER, small = true, enabled = !pendingRemove)
                    }
                    ClampButton(
                        when { same -> S.NO_CHANGE; raising -> S.REQUEST_24H; isNew -> S.SET_LIMIT; else -> S.APPLY_NOW },
                        { onSubmit(value) }, Modifier.weight(if (isNew) 1f else 1.85f), BtnStyle.PRIMARY, enabled = !same,
                    )
                }
            }
        }
    }
}
