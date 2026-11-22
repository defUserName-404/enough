package app.enough.setup.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.common.ui.S
import app.enough.common.ui.components.EnoughButton
import app.enough.common.ui.components.EnoughCheckBox
import app.enough.common.ui.components.EnoughRow
import app.enough.common.ui.components.Txt
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import app.enough.setup.domain.SetupMachine
import app.enough.setup.domain.SetupState

@Composable
internal fun PhoneTab(
    p: PhoneSetupUi,
    usageGranted: Boolean,
    onAccountsToggle: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenDevSettings: () -> Unit,
    onPair: () -> Unit,
    onCancelSetup: () -> Unit,
) {
    val c = LocalEnoughColors.current
    val state = p.view.state
    val running = !SetupMachine.terminal(state) && state != SetupState.Idle
    val failed = state is SetupState.Failed
    val stillOn = state is SetupState.DoneAdbStillOn
    val phase4 = SetupMachine.holdsActivation(state) || state is SetupState.Done || stillOn
    val canStart = usageGranted && p.accountsConfirmed && p.wifi && p.adbWifi

    Column {
        EnoughRow(onClick = onAccountsToggle) {
            EnoughCheckBox(p.accountsConfirmed)
            Column(Modifier.weight(1f)) {
                Txt(S.P1_TITLE, EnoughType.name)
                Txt(S.P1_SUB, EnoughType.label, c.dim, Modifier.padding(top = 4.dp))
            }
            EnoughButton(S.OPEN, onOpenAccounts, small = true)
        }
        EnoughRow {
            EnoughCheckBox(p.adbWifi && p.wifi)
            Column(Modifier.weight(1f)) {
                Txt(S.P2_TITLE, EnoughType.name)
                Txt(if (!p.wifi) S.P2_NO_WIFI else S.P2_SUB, EnoughType.label, if (!p.wifi) c.red else c.dim, Modifier.padding(top = 4.dp))
            }
            EnoughButton(S.OPEN, onOpenDevSettings, small = true)
        }
        EnoughRow {
            NumBox("3", active = !phase4, double = true)
            Column(Modifier.weight(1f)) {
                Txt(S.P3_TITLE, EnoughType.name)
                Txt(S.P3_SUB, EnoughType.label, c.dim, Modifier.padding(top = 4.dp))
                if (!usageGranted) Txt(S.GRANT_FIRST, EnoughType.label, c.red, Modifier.padding(top = 4.dp))
            }
            if (running) EnoughButton(S.CANCEL, onCancelSetup, small = true)
            else if (stillOn) EnoughButton(S.OPEN, onOpenDevSettings, small = true)
            else if (state !is SetupState.Done) {
                EnoughButton(if (failed) S.RETRY else S.START, onPair, small = true, enabled = canStart)
            }
        }
        EnoughRow {
            NumBox("4", active = phase4, double = false)
            Column(Modifier.weight(1f)) {
                Txt(S.P4_TITLE, EnoughType.name, if (phase4) c.fg else c.dim)
                Txt(S.P4_SUB, EnoughType.label, c.dim, Modifier.padding(top = 4.dp))
            }
        }
        if (p.notifDenied) Txt(S.NOTIF_DENIED, EnoughType.label, c.red, Modifier.padding(top = 12.dp))
        if (p.view.log.isNotEmpty()) {
            Column(Modifier.padding(top = 20.dp).fillMaxWidth().border(2.dp, c.fg).padding(16.dp)) {
                p.view.log.forEachIndexed { i, l ->
                    val last = i == p.view.log.lastIndex
                    val isFail = (failed || stillOn) && last
                    Txt(
                        "■ " + l + if (last && !SetupMachine.terminal(state)) " ▮" else "",
                        EnoughType.num.copy(fontSize = 13.sp), if (isFail) c.red else c.fg,
                        Modifier.padding(vertical = 3.dp),
                    )
                }
                if (state is SetupState.Failed && state.raw.isNotEmpty() && state.why == app.enough.setup.domain.SetupFailure.UNKNOWN) {
                    Txt(state.raw.take(120), EnoughType.label, c.red, Modifier.padding(top = 4.dp))
                }
                if (state is SetupState.AwaitingCode) Txt(S.NO_REPLY_HINT, EnoughType.label, c.dim, Modifier.padding(top = 8.dp))
            }
        }
        Txt(
            S.LOCAL_ONLY, EnoughType.label, c.dim,
            Modifier.fillMaxWidth().padding(top = 12.dp), maxLines = 3,
        )
    }
}

@Composable
internal fun NumBox(n: String, active: Boolean, double: Boolean) {
    val c = LocalEnoughColors.current
    val col = if (active) c.fg else c.dim
    Box(
        Modifier.size(24.dp).border(2.dp, col).let { if (double) it.padding(2.dp).border(2.dp, col) else it },
        contentAlignment = Alignment.Center,
    ) {
        Txt(n, EnoughType.label.copy(fontSize = 12.sp), col, textAlignCenter())
    }
}

internal fun textAlignCenter() = Modifier
