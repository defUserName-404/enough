package app.enough.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.BuildConfig
import app.enough.common.domain.dur
import app.enough.common.domain.durMin
import app.enough.common.domain.left
import app.enough.common.ui.S
import app.enough.common.ui.components.AppIcon
import app.enough.common.ui.components.BlockBar
import app.enough.common.ui.components.BtnStyle
import app.enough.common.ui.components.EnoughBox
import app.enough.common.ui.components.EnoughButton
import app.enough.common.ui.components.EnoughRow
import app.enough.common.ui.components.EnoughTabs
import app.enough.common.ui.components.Hairline
import app.enough.common.ui.components.LockIcon
import app.enough.common.ui.components.ProgressRing
import app.enough.common.ui.components.Txt
import app.enough.common.ui.components.plainClickable
import app.enough.common.ui.theme.Dims
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import app.enough.limits.domain.Change
import app.enough.limits.domain.Config
import app.enough.limits.domain.PendingChange
import app.enough.limits.domain.Target
import app.enough.settings.ui.GearButton
import app.enough.update.android.UpdateState
import app.enough.update.domain.ReleaseInfo
import app.enough.usage.domain.History
import app.enough.usage.domain.UsageSnapshot
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
    updateState: UpdateState,
    onInstallUpdate: (ReleaseInfo) -> Unit,
    history: History?,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUsage: () -> Unit,
) {
    val c = LocalEnoughColors.current
    val used = usage?.usage
    val limitedTotal = config.targets.fold(Duration.ZERO) { a, t -> a + (used?.get(t.pkg) ?: Duration.ZERO) }
    fun label(pkg: String) = (labels[pkg] ?: pkg).uppercase()

    Column(Modifier.fillMaxSize().padding(horizontal = Dims.Pad)) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(S.HOME_TITLE, EnoughType.h1.copy(fontSize = 30.sp, lineHeight = 30.sp), c.fg, Modifier.weight(1f))
                        GearButton(onOpenSettings)
                    }
                    Txt(
                        "${DATE_FMT.format(now.atZone(ZoneId.systemDefault())).uppercase()} · ${S.ACTIVE}",
                        EnoughType.label, c.fg, Modifier.padding(top = 6.dp),
                    )
                    UpdateBanner(updateState, onInstallUpdate)
                    if (config.protectionMissing.isNotEmpty()) {
                        Txt(
                            "${S.PROTECTION_INCOMPLETE}: ${config.protectionMissing.joinToString(", ")}",
                            EnoughType.label, c.red, Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 22.dp, bottom = 22.dp)) {
                    Txt(S.SCREEN_TIME_TODAY, EnoughType.label, c.fg)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Txt(
                            if (history == null) "--" else dur(maxOf(history.today.total, limitedTotal)),
                            EnoughType.big.copy(fontSize = 56.sp, letterSpacing = (-2).sp), c.fg, Modifier.weight(1f, fill = false),
                        )
                        // Padding is applied before drawBehind, so the draw size is the text's own box: the 2dp
                        // underline sits at its bottom edge. Never use fillMaxWidth here: inside a Row it would
                        // swallow all remaining width and squeeze the hours to nothing.
                        Txt(
                            S.USAGE_LINK, EnoughType.label.copy(fontSize = 12.sp), c.fg,
                            Modifier.padding(start = 12.dp)
                                .plainClickable(role = Role.Button, onClick = onOpenUsage)
                                .padding(vertical = 14.dp)
                                .drawBehind {
                                    val y = size.height - 1.dp.toPx()
                                    drawLine(c.fg, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                                },
                        )
                    }
                    Txt(
                        "${S.LIMITED_APPS} · ${if (used == null) "--" else dur(limitedTotal)}",
                        EnoughType.label.copy(fontSize = 12.sp), c.fg, Modifier.padding(top = 10.dp),
                    )
                }
            }
            item {
                Box(Modifier.padding(bottom = 8.dp)) {
                    EnoughTabs(listOf(S.TAB_LIMITS, "${S.PENDING} · ${config.pending.size}"), tab, onTab)
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
                            if (blocked) LockIcon(c.red, 16.dp)
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
            if (tab == 1) {
                if (config.pending.isEmpty()) item {
                    Column(Modifier.fillMaxWidth()) {
                        Hairline()
                        Txt(S.NO_PENDING, EnoughType.label.copy(textAlign = TextAlign.Center), c.dim, Modifier.fillMaxWidth().padding(vertical = 28.dp))
                    }
                } else items(config.pending, key = { it.id }) { p ->
                    Column(Modifier.fillMaxWidth()) {
                        Hairline()
                        PendingRow(p, config, now, ::label, onCancel)
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
        is Change.Remove -> label(ch.pkg) to S.REMOVE_PENDING
        Change.Release -> S.RELEASE_TITLE to ""
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
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

/** Home banner; only while a newer release exists or its install is in flight. Idle/up-to-date/failed render nothing. */
@Composable
private fun UpdateBanner(state: UpdateState, onInstall: (ReleaseInfo) -> Unit) {
    if (state !is UpdateState.Available && state !is UpdateState.Downloading && state != UpdateState.Installing) return
    val c = LocalEnoughColors.current
    Box(Modifier.padding(top = 14.dp)) {
        EnoughBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(if (state is UpdateState.Downloading) S.UPDATE_DOWNLOADING else S.UPDATE_AVAILABLE, EnoughType.label, c.fg)
                    val line = when (state) {
                        is UpdateState.Available -> "${BuildConfig.VERSION_NAME} → ${state.info.version}"
                        is UpdateState.Downloading -> S.UPDATE_KEEP_OPEN
                        else -> S.UPDATE_INSTALLING
                    }
                    Txt(line, EnoughType.num.copy(fontSize = 13.sp), c.fg, Modifier.padding(top = 4.dp))
                }
                when (state) {
                    is UpdateState.Available ->
                        EnoughButton(S.UPDATE_BTN, { onInstall(state.info) }, Modifier.padding(start = 12.dp), BtnStyle.PRIMARY, small = true)
                    is UpdateState.Downloading -> ProgressRing(state.percent, 56.dp, Modifier.padding(start = 12.dp))
                    else -> Unit
                }
            }
        }
    }
}
