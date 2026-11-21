package app.enough.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.core.Change
import app.enough.core.Config
import app.enough.core.PendingChange
import app.enough.core.Target
import app.enough.core.UsageSnapshot
import app.enough.core.History
import app.enough.core.dur
import app.enough.core.durMin
import app.enough.core.left
import app.enough.ui.components.AppIcon
import app.enough.ui.components.BlockBar
import app.enough.ui.components.BtnStyle
import app.enough.ui.components.EnoughBox
import app.enough.ui.components.EnoughButton
import app.enough.ui.components.EnoughRow
import app.enough.ui.components.EnoughTabs
import app.enough.ui.components.InvertBox
import app.enough.ui.components.Txt
import app.enough.ui.theme.EnoughType
import app.enough.core.ReleaseInfo
import app.enough.update.UpdateState
import app.enough.BuildConfig
import app.enough.ui.theme.Dims
import app.enough.ui.theme.LocalEnoughColors
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

@Composable
fun HomeScreen(
    config: Config,
    usage: UsageSnapshot?,
    labels: Map<String, String>,
    now: Instant,
    onEdit: (Target) -> Unit,
    onAdd: () -> Unit,
    onCancel: (String) -> Unit,
    onRelease: () -> Unit,
    updateState: UpdateState,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: (ReleaseInfo) -> Unit,
    history: History?,
    noAccess: Boolean,
    tab: Int,
    onTab: (Int) -> Unit,
    unpickable: Set<String>,
    onSetLimit: (String) -> Unit,
) {
    val c = LocalEnoughColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val used = usage?.usage
    val limitedTotal = config.targets.fold(Duration.ZERO) { a, t -> a + (used?.get(t.pkg) ?: Duration.ZERO) }
    val releasePending = config.pending.any { it.change is Change.Release }
    fun label(pkg: String) = (labels[pkg] ?: pkg).uppercase()

    Column(Modifier.fillMaxSize().padding(horizontal = Dims.Pad)) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(S.HOME_TITLE, EnoughType.h1.copy(fontSize = 30.sp, lineHeight = 30.sp), c.fg, Modifier.weight(1f))
                        InvertBox({ menuOpen = !menuOpen }, Modifier.size(Dims.Touch), desc = "Menu") {
                            Txt("▪▪", EnoughType.button.copy(fontSize = 20.sp), it)
                        }
                    }
                    if (menuOpen) {
                        Box(Modifier.padding(top = 8.dp)) {
                            UpdateBox(updateState, onCheckUpdate, onInstallUpdate)
                        }
                        Box(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                            EnoughBox(red = true) {
                                Txt(S.RELEASE_TITLE, EnoughType.label.copy(fontSize = 12.sp), c.red)
                                Txt(S.RELEASE_BODY, EnoughType.body.copy(fontSize = 13.5.sp), c.fg, Modifier.padding(top = 6.dp))
                                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    EnoughButton(S.CANCEL, { menuOpen = false }, Modifier.weight(1f), small = true)
                                    EnoughButton(
                                        if (releasePending) S.RELEASE_PENDING else S.RELEASE_BTN,
                                        { menuOpen = false; onRelease() },
                                        Modifier.weight(2f), BtnStyle.DANGER, small = true, enabled = !releasePending,
                                    )
                                }
                            }
                        }
                    }
                    Txt(
                        "${DATE_FMT.format(now.atZone(ZoneId.systemDefault())).uppercase()} · ${S.ACTIVE}",
                        EnoughType.label, c.fg, Modifier.padding(top = 6.dp),
                    )
                    if (config.protectionMissing.isNotEmpty()) {
                        Txt(
                            "${S.PROTECTION_INCOMPLETE}: ${config.protectionMissing.joinToString(", ")}",
                            EnoughType.label, c.red, Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            if (tab == 0) {
                item {
                    Column(Modifier.padding(top = 22.dp, bottom = 22.dp)) {
                        Txt(S.SCREEN_TIME_TODAY, EnoughType.label, c.fg)
                        Txt(
                            if (history == null) "--" else dur(maxOf(history.today.total, limitedTotal)),
                            EnoughType.big.copy(fontSize = 56.sp, letterSpacing = (-2).sp), c.fg, Modifier.padding(top = 6.dp),
                        )
                        Txt(
                            "${S.LIMITED_APPS} · ${if (used == null) "--" else dur(limitedTotal)}",
                            EnoughType.label.copy(fontSize = 12.sp), c.fg, Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            item {
                Box(Modifier.padding(top = if (tab == 0) 0.dp else 16.dp, bottom = 8.dp)) {
                    EnoughTabs(listOf(S.TAB_LIMITS, S.TAB_USAGE), tab, onTab)
                }
            }
            if (tab == 1) {
                item {
                    UsageTab(history, noAccess, labels, config, used, now, unpickable, onSetLimit)
                }
            }
            if (tab == 0) items(config.targets, key = { it.pkg }) { t ->
                val u = used?.get(t.pkg) ?: Duration.ZERO
                val limit = Duration.ofMinutes(t.limitMin.toLong())
                val revoked = used == null
                val blocked = revoked || u >= limit
                val cant = t.pkg in config.unsuspendable
                EnoughRow(onClick = { onEdit(t) }) {
                    AppIcon(t.pkg, blocked = blocked)
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Txt(label(t.pkg), EnoughType.name, if (blocked) c.red else c.fg, Modifier.weight(1f), maxLines = 1)
                            if (blocked) Txt(S.LOCKED, EnoughType.num.copy(fontSize = 13.sp, letterSpacing = 1.5.sp), c.red)
                            else Txt("${dur(u)} / ${durMin(t.limitMin)}", EnoughType.num.copy(fontSize = 13.sp), c.fg)
                        }
                        Box(Modifier.padding(top = 8.dp)) {
                            BlockBar(if (revoked) 1f else (u.seconds.toFloat() / limit.seconds.toFloat()), red = blocked)
                        }
                        if (blocked) {
                            Txt(
                                if (revoked) S.USAGE_REVOKED else "${dur(limit)} / ${durMin(t.limitMin)} · OPENS 00:00",
                                EnoughType.label, if (revoked) c.red else c.dim, Modifier.padding(top = 6.dp),
                            )
                        }
                        if (cant) Txt(S.CANT_BLOCK, EnoughType.label, c.red, Modifier.padding(top = 6.dp))
                    }
                }
            }
            if (tab == 0 && config.pending.isNotEmpty()) {
                item {
                    Box(Modifier.padding(top = 22.dp)) {
                        EnoughBox {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Txt("${S.PENDING} · ${config.pending.size}", EnoughType.label, c.fg)
                                Txt(S.LOOSENING, EnoughType.label, c.dim)
                            }
                            config.pending.forEach { p -> PendingRow(p, config, now, ::label, onCancel) }
                        }
                    }
                }
            }
        }
        if (tab == 0) Box(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
            EnoughButton(S.ADD_APP_BTN, onAdd, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PendingRow(
    p: PendingChange,
    config: Config,
    now: Instant,
    label: (String) -> String,
    onCancel: (String) -> Unit,
) {
    val c = LocalEnoughColors.current
    val (name, delta) = when (val ch = p.change) {
        is Change.RaiseLimit -> label(ch.target.pkg) to
            "${config.target(ch.target.pkg)?.limitMin?.let(::durMin) ?: "--"} → ${durMin(ch.target.limitMin)}"
        is Change.AddOrLower -> label(ch.target.pkg) to
            "${config.target(ch.target.pkg)?.limitMin?.let(::durMin) ?: "--"} → ${durMin(ch.target.limitMin)}"
        is Change.Remove -> label(ch.pkg) to S.REMOVE
        Change.Release -> S.RELEASE_TITLE to ""
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Txt(name, EnoughType.name, c.fg, Modifier.weight(1f), maxLines = 1)
            Txt(delta, EnoughType.num.copy(fontSize = 13.sp), c.fg)
        }
        Txt(
            left(Duration.between(now, p.applyAt)),
            EnoughType.big.copy(fontSize = 30.sp, letterSpacing = (-1).sp), c.fg, Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            EnoughButton(S.CANCEL_REQUEST, { onCancel(p.id) }, Modifier.weight(1f), small = true)
        }
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
            is UpdateState.Downloading -> "${S.UPDATE_DOWNLOADING} ${state.percent}%" to false
            UpdateState.Installing -> S.UPDATE_INSTALLING to false
            is UpdateState.Failed -> state.message to true
            else -> null to false
        }
        if (line != null) Txt(line, EnoughType.body.copy(fontSize = 13.5.sp), if (red) c.red else c.fg, Modifier.padding(top = 6.dp))
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
