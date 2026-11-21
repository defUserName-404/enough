package dev.clamp.ui

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
import dev.clamp.core.Change
import dev.clamp.core.Config
import dev.clamp.core.PendingChange
import dev.clamp.core.Target
import dev.clamp.core.UsageSnapshot
import dev.clamp.core.History
import dev.clamp.core.dur
import dev.clamp.core.durMin
import dev.clamp.core.left
import dev.clamp.ui.components.AppIcon
import dev.clamp.ui.components.BlockBar
import dev.clamp.ui.components.BtnStyle
import dev.clamp.ui.components.ClampBox
import dev.clamp.ui.components.ClampButton
import dev.clamp.ui.components.ClampRow
import dev.clamp.ui.components.ClampTabs
import dev.clamp.ui.components.InvertBox
import dev.clamp.ui.components.Txt
import dev.clamp.ui.theme.ClampType
import dev.clamp.ui.theme.Dims
import dev.clamp.ui.theme.LocalClampColors
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
    history: History?,
    noAccess: Boolean,
    tab: Int,
    onTab: (Int) -> Unit,
    unpickable: Set<String>,
    onSetLimit: (String) -> Unit,
) {
    val c = LocalClampColors.current
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
                        Txt(S.HOME_TITLE, ClampType.h1.copy(fontSize = 30.sp, lineHeight = 30.sp), c.fg, Modifier.weight(1f))
                        InvertBox({ menuOpen = !menuOpen }, Modifier.size(Dims.Touch), desc = "Menu") {
                            Txt("▪▪", ClampType.button.copy(fontSize = 20.sp), it)
                        }
                    }
                    if (menuOpen) {
                        Box(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                            ClampBox(red = true) {
                                Txt(S.RELEASE_TITLE, ClampType.label.copy(fontSize = 12.sp), c.red)
                                Txt(S.RELEASE_BODY, ClampType.body.copy(fontSize = 13.5.sp), c.fg, Modifier.padding(top = 6.dp))
                                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ClampButton(S.CANCEL, { menuOpen = false }, Modifier.weight(1f), small = true)
                                    ClampButton(
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
                        ClampType.label, c.fg, Modifier.padding(top = 6.dp),
                    )
                    if (config.protectionMissing.isNotEmpty()) {
                        Txt(
                            "${S.PROTECTION_INCOMPLETE}: ${config.protectionMissing.joinToString(", ")}",
                            ClampType.label, c.red, Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            if (tab == 0) {
                item {
                    Column(Modifier.padding(top = 22.dp, bottom = 22.dp)) {
                        Txt(S.SCREEN_TIME_TODAY, ClampType.label, c.fg)
                        Txt(
                            if (history == null) "--" else dur(maxOf(history.today.total, limitedTotal)),
                            ClampType.big.copy(fontSize = 56.sp, letterSpacing = (-2).sp), c.fg, Modifier.padding(top = 6.dp),
                        )
                        Txt(
                            "${S.LIMITED_APPS} · ${if (used == null) "--" else dur(limitedTotal)}",
                            ClampType.label.copy(fontSize = 12.sp), c.fg, Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            item {
                Box(Modifier.padding(top = if (tab == 0) 0.dp else 16.dp, bottom = 8.dp)) {
                    ClampTabs(listOf(S.TAB_LIMITS, S.TAB_USAGE), tab, onTab)
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
                ClampRow(onClick = { onEdit(t) }) {
                    AppIcon(t.pkg, blocked = blocked)
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Txt(label(t.pkg), ClampType.name, if (blocked) c.red else c.fg, Modifier.weight(1f), maxLines = 1)
                            if (blocked) Txt(S.LOCKED, ClampType.num.copy(fontSize = 13.sp, letterSpacing = 1.5.sp), c.red)
                            else Txt("${dur(u)} / ${durMin(t.limitMin)}", ClampType.num.copy(fontSize = 13.sp), c.fg)
                        }
                        Box(Modifier.padding(top = 8.dp)) {
                            BlockBar(if (revoked) 1f else (u.seconds.toFloat() / limit.seconds.toFloat()), red = blocked)
                        }
                        if (blocked) {
                            Txt(
                                if (revoked) S.USAGE_REVOKED else "${dur(limit)} / ${durMin(t.limitMin)} · OPENS 00:00",
                                ClampType.label, if (revoked) c.red else c.dim, Modifier.padding(top = 6.dp),
                            )
                        }
                        if (cant) Txt(S.CANT_BLOCK, ClampType.label, c.red, Modifier.padding(top = 6.dp))
                    }
                }
            }
            if (tab == 0 && config.pending.isNotEmpty()) {
                item {
                    Box(Modifier.padding(top = 22.dp)) {
                        ClampBox {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Txt("${S.PENDING} · ${config.pending.size}", ClampType.label, c.fg)
                                Txt(S.LOOSENING, ClampType.label, c.dim)
                            }
                            config.pending.forEach { p -> PendingRow(p, config, now, ::label, onCancel) }
                        }
                    }
                }
            }
        }
        if (tab == 0) Box(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
            ClampButton(S.ADD_APP_BTN, onAdd, Modifier.fillMaxWidth())
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
    val c = LocalClampColors.current
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
            Txt(name, ClampType.name, c.fg, Modifier.weight(1f), maxLines = 1)
            Txt(delta, ClampType.num.copy(fontSize = 13.sp), c.fg)
        }
        Txt(
            left(Duration.between(now, p.applyAt)),
            ClampType.big.copy(fontSize = 30.sp, letterSpacing = (-1).sp), c.fg, Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            ClampButton(S.CANCEL_REQUEST, { onCancel(p.id) }, Modifier.weight(1f), small = true)
        }
    }
}
