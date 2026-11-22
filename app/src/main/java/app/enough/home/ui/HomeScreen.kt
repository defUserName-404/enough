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
import app.enough.common.domain.dur
import app.enough.common.domain.durMin
import app.enough.common.ui.S
import app.enough.common.ui.components.AppIcon
import app.enough.common.ui.components.BlockBar
import app.enough.common.ui.components.EnoughButton
import app.enough.common.ui.components.EnoughRow
import app.enough.common.ui.components.EnoughTabs
import app.enough.common.ui.components.GearButton
import app.enough.common.ui.components.Hairline
import app.enough.common.ui.components.LockIcon
import app.enough.common.ui.components.Txt
import app.enough.common.ui.components.plainClickable
import app.enough.common.ui.theme.Dims
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import app.enough.limits.domain.Target
import app.enough.update.domain.ReleaseInfo
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onEdit: (Target) -> Unit,
    onAdd: () -> Unit,
    onCancel: (String) -> Unit,
    onInstallUpdate: (ReleaseInfo) -> Unit,
    onTab: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUsage: () -> Unit,
) {
    val config = state.config
    val usage = state.usage
    val labels = state.labels
    val now = state.now
    val updateState = state.updateState
    val history = state.history
    val tab = state.tab
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
