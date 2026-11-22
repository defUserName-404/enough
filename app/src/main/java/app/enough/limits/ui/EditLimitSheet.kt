package app.enough.limits.ui

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
import app.enough.common.domain.durMin
import app.enough.common.domain.left
import app.enough.common.ui.S
import app.enough.common.ui.components.AppIcon
import app.enough.common.ui.components.BtnStyle
import app.enough.common.ui.components.EnoughBox
import app.enough.common.ui.components.EnoughButton
import app.enough.common.ui.components.Hairline
import app.enough.common.ui.components.InvertBox
import app.enough.common.ui.components.Scrim
import app.enough.common.ui.components.Stepper
import app.enough.common.ui.components.Txt
import app.enough.common.ui.theme.Dims
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import app.enough.limits.domain.Change
import app.enough.limits.domain.PendingChange
import app.enough.limits.domain.Target
import java.time.Duration
import java.time.Instant

@Composable
fun EditLimitSheet(
    state: EditLimitUiState,
    onSubmit: (Int) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val target = state.target
    val label = state.label
    val pending = state.pending
    val now = state.now
    val isNew = state.isNew
    val c = LocalEnoughColors.current
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
                    Txt(label.uppercase(), EnoughType.h2, c.fg, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
                    InvertBox(onClose, Modifier.size(Dims.Touch), desc = "Close") { Txt("✕", EnoughType.num, it) }
                }
                Txt("${S.DAILY_LIMIT} · NOW ${if (isNew) "NONE" else durMin(target.limitMin)}", EnoughType.label, c.fg, Modifier.padding(top = 20.dp, bottom = 10.dp))
                Stepper(value, { value = it }, large = true)
                Box(Modifier.padding(top = 14.dp)) {
                    EnoughBox(red = raising) {
                        // Headline only once the user has moved the stepper; the note explains both directions.
                        if (!same) Txt(
                            if (isNew) S.NEW_LIMIT else if (raising) S.RAISING else S.LOWERING,
                            EnoughType.label.copy(fontSize = 12.sp), if (raising) c.red else c.fg,
                            Modifier.padding(bottom = 6.dp),
                        )
                        Txt(S.SHEET_NOTE, EnoughType.body.copy(fontSize = 13.5.sp), c.fg)
                    }
                }
                if (pendingRemove && !same) {
                    Txt(
                        "THIS CANCELS THE PENDING REMOVE (${left(Duration.between(now, pending!!.applyAt))})",
                        EnoughType.label, c.red, Modifier.padding(top = 10.dp),
                    )
                } else if (pending != null && !isNew) Txt(S.REPLACES_PENDING, EnoughType.label, c.dim, Modifier.padding(top = 10.dp))
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isNew) {
                        EnoughButton(S.REMOVE, onRemove, Modifier.weight(1.15f), BtnStyle.DANGER, small = true, enabled = !pendingRemove)
                    }
                    EnoughButton(
                        when { same -> S.NO_CHANGE; raising -> S.REQUEST_24H; isNew -> S.SET_LIMIT; else -> S.APPLY_NOW },
                        { onSubmit(value) }, Modifier.weight(if (isNew) 1f else 1.85f), BtnStyle.PRIMARY, enabled = !same,
                    )
                }
            }
        }
    }
}
