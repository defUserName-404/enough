package app.enough.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.BuildConfig
import app.enough.common.ui.S
import app.enough.common.ui.components.BackHeader
import app.enough.common.ui.components.BtnStyle
import app.enough.common.ui.components.EnoughBox
import app.enough.common.ui.components.EnoughButton
import app.enough.common.ui.components.ProgressRing
import app.enough.common.ui.components.Txt
import app.enough.common.ui.theme.Dims
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import app.enough.limits.domain.Change
import app.enough.update.domain.ReleaseInfo
import app.enough.update.domain.UpdateState

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: (ReleaseInfo) -> Unit,
    onRelease: () -> Unit,
    onBack: () -> Unit,
) {
    val config = state.config
    val updateState = state.updateState
    val c = LocalEnoughColors.current
    val releasePending = config.pending.any { it.change is Change.Release }
    Column(Modifier.fillMaxSize().padding(horizontal = Dims.Pad)) {
        BackHeader(S.SETTINGS_TITLE, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
            UpdateBox(updateState, onCheckUpdate, onInstallUpdate)
            Column(Modifier.padding(top = 16.dp)) {
                EnoughBox(red = true) {
                    Txt(S.RELEASE_TITLE, EnoughType.label.copy(fontSize = 12.sp), c.red)
                    Txt(S.RELEASE_BODY, EnoughType.body.copy(fontSize = 13.5.sp), c.fg, Modifier.padding(top = 6.dp))
                    EnoughButton(
                        if (releasePending) S.RELEASE_PENDING else S.RELEASE_BTN, onRelease,
                        Modifier.fillMaxWidth().padding(top = 14.dp), BtnStyle.DANGER, small = true, enabled = !releasePending,
                    )
                }
            }
        }
        Txt(S.LOCAL_ONLY, EnoughType.label, c.dim, Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun UpdateBox(state: UpdateState, onCheck: () -> Unit, onInstall: (ReleaseInfo) -> Unit) {
    val c = LocalEnoughColors.current
    EnoughBox {
        Txt(S.UPDATE_TITLE, EnoughType.label.copy(fontSize = 12.sp), c.fg)
        Txt("${S.UPDATE_VERSION} ${BuildConfig.VERSION_NAME}", EnoughType.body.copy(fontSize = 13.5.sp), c.dim, Modifier.padding(top = 6.dp))
        val (line, red) = when (state) {
            UpdateState.UpToDate -> S.UPDATE_UP_TO_DATE to false
            UpdateState.Installing -> S.UPDATE_INSTALLING to false
            is UpdateState.Failed -> state.message to true
            else -> null to false
        }
        if (line != null) Txt(line, EnoughType.body.copy(fontSize = 13.5.sp), if (red) c.red else c.fg, Modifier.padding(top = 6.dp))
        if (state is UpdateState.Downloading) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(state.percent, 64.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Txt(S.UPDATE_DOWNLOADING, EnoughType.label.copy(fontSize = 12.sp), c.fg)
                    Txt(S.UPDATE_KEEP_OPEN, EnoughType.body.copy(fontSize = 13.5.sp), c.dim, Modifier.padding(top = 4.dp))
                }
            }
            return@EnoughBox
        }
        val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state == UpdateState.Installing
        val (label, action) = when (state) {
            is UpdateState.Available -> "${S.UPDATE_INSTALL} ${state.info.version}" to { onInstall(state.info) }
            UpdateState.Checking -> S.UPDATE_CHECKING to onCheck
            is UpdateState.Failed -> S.UPDATE_RETRY to onCheck
            else -> S.UPDATE_CHECK to onCheck
        }
        EnoughButton(
            label, action, Modifier.fillMaxWidth().padding(top = 14.dp),
            if (state is UpdateState.Available) BtnStyle.PRIMARY else BtnStyle.OUTLINE, small = true, enabled = !busy,
        )
    }
}
