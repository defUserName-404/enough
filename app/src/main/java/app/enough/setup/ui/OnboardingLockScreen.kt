package app.enough.setup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.common.ui.S
import app.enough.common.ui.components.BtnStyle
import app.enough.common.ui.components.EnoughButton
import app.enough.common.ui.components.EnoughCheckBox
import app.enough.common.ui.components.EnoughRow
import app.enough.common.ui.components.InvertBox
import app.enough.common.ui.components.Txt
import app.enough.common.ui.components.plainClickable
import app.enough.common.ui.theme.Dims
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import app.enough.oem.domain.OemSpec

@Composable
fun OnboardingLockScreen(
    state: SetupUiState,
    onUsage: () -> Unit,
    onOem: (OemSpec) -> Unit,
    onAccountsToggle: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenDevSettings: () -> Unit,
    onPair: () -> Unit,
    onCancelSetup: () -> Unit,
    onBack: () -> Unit,
) {
    val usageGranted = state.usageGranted
    val oemItems = state.oemItems
    val oemDone = state.oemDone
    val phone = state.phone
    val c = LocalEnoughColors.current
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 = phone (default), 1 = computer

    Column(Modifier.fillMaxSize().padding(horizontal = Dims.Pad)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(S.SETUP_2, EnoughType.label, c.fg, Modifier.weight(1f))
                InvertBox(onBack, Modifier.size(Dims.Touch), desc = "Back") { Txt("←", EnoughType.num, it) }
            }
            Txt(S.LOCK_TITLE, EnoughType.h1, c.fg, Modifier.padding(top = 14.dp))
            Txt(S.LOCK_BODY, EnoughType.body, c.fg, Modifier.padding(top = 14.dp))
            Column(Modifier.padding(top = 20.dp)) {
                EnoughRow {
                    EnoughCheckBox(usageGranted)
                    Column(Modifier.weight(1f)) {
                        Txt(S.USAGE_ACCESS, EnoughType.name)
                        Txt(if (usageGranted) S.GRANTED else S.REQUIRED, EnoughType.label, c.dim, Modifier.padding(top = 4.dp))
                    }
                    if (!usageGranted) EnoughButton(S.OPEN, onUsage, small = true)
                }
                oemItems.forEach { spec ->
                    val done = spec.id in oemDone
                    EnoughRow {
                        EnoughCheckBox(done)
                        Column(Modifier.weight(1f)) {
                            Txt(spec.title, EnoughType.name)
                            Txt(
                                if (done) spec.subtitle.replace("REQUIRED", "ENABLED") else spec.subtitle,
                                EnoughType.label, c.dim, Modifier.padding(top = 4.dp),
                            )
                        }
                        if (!done) EnoughButton(S.OPEN, { onOem(spec) }, small = true)
                    }
                }
            }

            // Tab bar
            Row(Modifier.padding(top = 20.dp).fillMaxWidth().border(2.dp, c.fg)) {
                listOf(S.TAB_PHONE, S.TAB_COMPUTER).forEachIndexed { i, label ->
                    val sel = tab == i
                    Box(
                        Modifier.weight(1f).heightIn(min = 48.dp).background(if (sel) c.fg else Color.Transparent)
                            .plainClickable(role = Role.Tab) { tab = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt(label, EnoughType.label.copy(fontSize = 12.sp), if (sel) c.bg else c.fg, Modifier.padding(8.dp))
                    }
                }
            }

            if (tab == 0) {
                PhoneTab(phone, usageGranted, onAccountsToggle, onOpenAccounts, onOpenDevSettings, onPair, onCancelSetup)
            } else {
                ComputerTab(usageGranted)
            }
            Box(Modifier.size(12.dp))
        }
        if (tab == 1) {
            EnoughButton(
                if (usageGranted) S.WAITING else S.GRANT_FIRST,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                style = BtnStyle.GHOST,
                enabled = false,
            )
        }
        Box(Modifier.size(16.dp))
    }
}
