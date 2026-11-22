package app.enough.usage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.enough.common.ui.S
import app.enough.common.ui.components.BackHeader
import app.enough.common.ui.components.Txt
import app.enough.common.ui.theme.Dims
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import java.time.ZoneId

/** Standalone Usage screen, opened from the Home "USAGE →" link. */
@Composable
fun UsageScreen(
    state: UsageUiState,
    onSetLimit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val history = state.history
    val noAccess = state.noAccess
    val labels = state.labels
    val config = state.config
    val used = state.used
    val now = state.now
    val unpickable = state.unpickable
    val c = LocalEnoughColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = Dims.Pad)) {
        BackHeader(S.USAGE_TITLE, onBack)
        Txt(
            DESC_FMT.format(now.atZone(ZoneId.systemDefault())).uppercase(),
            EnoughType.label, c.fg, Modifier.padding(top = 6.dp),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            UsageTab(history, noAccess, labels, config, used, now, unpickable, onSetLimit)
        }
    }
}
