package dev.clamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.clamp.ClampApp
import dev.clamp.core.SetupMachine
import dev.clamp.core.SetupState
import dev.clamp.core.SetupView
import dev.clamp.oem.OemItem
import dev.clamp.ui.components.BtnStyle
import dev.clamp.ui.components.ClampButton
import dev.clamp.ui.components.ClampCheckBox
import dev.clamp.ui.components.ClampRow
import dev.clamp.ui.components.InvertBox
import dev.clamp.ui.components.Txt
import dev.clamp.ui.components.plainClickable
import dev.clamp.ui.theme.ClampType
import dev.clamp.ui.theme.Dims
import dev.clamp.ui.theme.LocalClampColors
import kotlinx.coroutines.delay

data class PhoneSetupUi(
    val wifi: Boolean,
    val adbWifi: Boolean,
    val accountsConfirmed: Boolean,
    val view: SetupView,
    val notifDenied: Boolean = false,
)

@Composable
fun OnboardingLockScreen(
    usageGranted: Boolean,
    oemItems: List<OemItem>,
    oemDone: Set<String>,
    phone: PhoneSetupUi,
    onUsage: () -> Unit,
    onOem: (OemItem) -> Unit,
    onAccountsToggle: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenDevSettings: () -> Unit,
    onPair: () -> Unit,
    onCancelSetup: () -> Unit,
    onBack: () -> Unit,
    onActivated: () -> Unit,
) {
    val c = LocalClampColors.current
    val graph = (LocalContext.current.applicationContext as ClampApp).graph
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 = phone (default), 1 = computer

    // Usage access is required first, otherwise activation would instantly fail closed.
    // The setup gate guarantees adb wifi goes off before DISALLOW_DEBUGGING_FEATURES is applied.
    LaunchedEffect(usageGranted) {
        if (!usageGranted) return@LaunchedEffect
        while (true) {
            if (graph.policy.isActive() && graph.setup.canActivate()) {
                graph.controller.onDeviceOwnerDetected()
                onActivated()
                break
            }
            delay(2_000)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Dims.Pad)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(S.SETUP_2, ClampType.label, c.fg, Modifier.weight(1f))
                InvertBox(onBack, Modifier.size(Dims.Touch), desc = "Back") { Txt("←", ClampType.num, it) }
            }
            Txt(S.LOCK_TITLE, ClampType.h1, c.fg, Modifier.padding(top = 14.dp))
            Txt(S.LOCK_BODY, ClampType.body, c.fg, Modifier.padding(top = 14.dp))
            Column(Modifier.padding(top = 20.dp)) {
                ClampRow {
                    ClampCheckBox(usageGranted)
                    Column(Modifier.weight(1f)) {
                        Txt(S.USAGE_ACCESS, ClampType.name)
                        Txt(if (usageGranted) S.GRANTED else S.REQUIRED, ClampType.label, c.dim, Modifier.padding(top = 4.dp))
                    }
                    if (!usageGranted) ClampButton(S.OPEN, onUsage, small = true)
                }
                oemItems.forEach { item ->
                    val done = item.spec.id in oemDone
                    ClampRow {
                        ClampCheckBox(done)
                        Column(Modifier.weight(1f)) {
                            Txt(item.spec.title, ClampType.name)
                            Txt(
                                if (done) item.spec.subtitle.replace("REQUIRED", "ENABLED") else item.spec.subtitle,
                                ClampType.label, c.dim, Modifier.padding(top = 4.dp),
                            )
                        }
                        if (!done) ClampButton(S.OPEN, { onOem(item) }, small = true)
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
                        Txt(label, ClampType.label.copy(fontSize = 12.sp), if (sel) c.bg else c.fg, Modifier.padding(8.dp))
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
            ClampButton(
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

@Composable
private fun PhoneTab(
    p: PhoneSetupUi,
    usageGranted: Boolean,
    onAccountsToggle: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenDevSettings: () -> Unit,
    onPair: () -> Unit,
    onCancelSetup: () -> Unit,
) {
    val c = LocalClampColors.current
    val state = p.view.state
    val running = !SetupMachine.terminal(state) && state != SetupState.Idle
    val failed = state is SetupState.Failed
    val stillOn = state is SetupState.DoneAdbStillOn
    val phase4 = SetupMachine.holdsActivation(state) || state is SetupState.Done || stillOn
    val canStart = usageGranted && p.accountsConfirmed && p.wifi && p.adbWifi

    Column {
        ClampRow(onClick = onAccountsToggle) {
            ClampCheckBox(p.accountsConfirmed)
            Column(Modifier.weight(1f)) {
                Txt(S.P1_TITLE, ClampType.name)
                Txt(S.P1_SUB, ClampType.label, c.dim, Modifier.padding(top = 4.dp))
            }
            ClampButton(S.OPEN, onOpenAccounts, small = true)
        }
        ClampRow {
            ClampCheckBox(p.adbWifi && p.wifi)
            Column(Modifier.weight(1f)) {
                Txt(S.P2_TITLE, ClampType.name)
                Txt(if (!p.wifi) S.P2_NO_WIFI else S.P2_SUB, ClampType.label, if (!p.wifi) c.red else c.dim, Modifier.padding(top = 4.dp))
            }
            ClampButton(S.OPEN, onOpenDevSettings, small = true)
        }
        ClampRow {
            NumBox("3", active = !phase4, double = true)
            Column(Modifier.weight(1f)) {
                Txt(S.P3_TITLE, ClampType.name)
                Txt(S.P3_SUB, ClampType.label, c.dim, Modifier.padding(top = 4.dp))
                if (!usageGranted) Txt(S.GRANT_FIRST, ClampType.label, c.red, Modifier.padding(top = 4.dp))
            }
            if (running) ClampButton(S.CANCEL, onCancelSetup, small = true)
            else if (stillOn) ClampButton(S.OPEN, onOpenDevSettings, small = true)
            else if (state !is SetupState.Done) {
                ClampButton(if (failed) S.RETRY else S.START, onPair, small = true, enabled = canStart)
            }
        }
        ClampRow {
            NumBox("4", active = phase4, double = false)
            Column(Modifier.weight(1f)) {
                Txt(S.P4_TITLE, ClampType.name, if (phase4) c.fg else c.dim)
                Txt(S.P4_SUB, ClampType.label, c.dim, Modifier.padding(top = 4.dp))
            }
        }
        if (p.notifDenied) Txt(S.NOTIF_DENIED, ClampType.label, c.red, Modifier.padding(top = 12.dp))
        if (p.view.log.isNotEmpty()) {
            Column(Modifier.padding(top = 20.dp).fillMaxWidth().border(2.dp, c.fg).padding(16.dp)) {
                p.view.log.forEachIndexed { i, l ->
                    val last = i == p.view.log.lastIndex
                    val isFail = (failed || stillOn) && last
                    Txt(
                        "■ " + l + if (last && !SetupMachine.terminal(state)) " ▮" else "",
                        ClampType.num.copy(fontSize = 13.sp), if (isFail) c.red else c.fg,
                        Modifier.padding(vertical = 3.dp),
                    )
                }
                if (state is SetupState.Failed && state.raw.isNotEmpty() && state.why == dev.clamp.core.SetupFailure.UNKNOWN) {
                    Txt(state.raw.take(120), ClampType.label, c.red, Modifier.padding(top = 4.dp))
                }
                if (state is SetupState.AwaitingCode) Txt(S.NO_REPLY_HINT, ClampType.label, c.dim, Modifier.padding(top = 8.dp))
            }
        }
        Txt(
            S.LOCAL_ONLY, ClampType.label, c.dim,
            Modifier.fillMaxWidth().padding(top = 12.dp), maxLines = 3,
        )
    }
}

@Composable
private fun NumBox(n: String, active: Boolean, double: Boolean) {
    val c = LocalClampColors.current
    val col = if (active) c.fg else c.dim
    Box(
        Modifier.size(24.dp).border(2.dp, col).let { if (double) it.padding(2.dp).border(2.dp, col) else it },
        contentAlignment = Alignment.Center,
    ) {
        Txt(n, ClampType.label.copy(fontSize = 12.sp), col, textAlignCenter())
    }
}

private fun textAlignCenter() = Modifier

@Composable
private fun ComputerTab(usageGranted: Boolean) {
    val c = LocalClampColors.current
    val clip = LocalClipboardManager.current
    Txt(S.RUN_ON_COMPUTER, ClampType.label, c.fg, Modifier.padding(top = 22.dp))
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(c.fg).border(2.dp, c.fg).padding(16.dp)) {
        Txt(S.ADB_DISPLAY, ClampType.num.copy(fontSize = 12.5.sp), c.bg)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            InvertBox(
                { clip.setText(AnnotatedString(S.ADB_CMD)) },
                Modifier.border(2.dp, c.bg).padding(horizontal = 10.dp, vertical = 6.dp),
                desc = "Copy command",
            ) { Txt(S.COPY, ClampType.label.copy(fontSize = 11.sp), c.bg) }
        }
    }
    Column(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
        listOf(S.STEP_1, S.STEP_2, S.STEP_3).forEach {
            Txt(it, ClampType.label.copy(fontSize = 11.5.sp, lineHeight = 20.sp), c.dim)
        }
    }
}
