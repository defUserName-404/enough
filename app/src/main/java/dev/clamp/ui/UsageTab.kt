package dev.clamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.clamp.core.AppHistory
import dev.clamp.core.Config
import dev.clamp.core.History
import dev.clamp.core.Trend
import dev.clamp.core.axisLabel
import dev.clamp.core.dur
import dev.clamp.core.durMin
import dev.clamp.ui.components.AppIcon
import dev.clamp.ui.components.ClampRow
import dev.clamp.ui.components.Hairline
import dev.clamp.ui.components.Txt
import dev.clamp.ui.components.plainClickable
import dev.clamp.ui.theme.ClampType
import dev.clamp.ui.theme.LocalClampColors
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DESC_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val CHART_H = 160.dp
private val LABEL_H = 18.dp

private fun trendText(t: Trend) = (if (t.up) "▲ " else "▼ ") + "${t.pct}%"

@Composable
fun UsageTab(
    history: History?,
    noAccess: Boolean,
    labels: Map<String, String>,
    config: Config,
    used: Map<String, Duration>?,
    now: Instant,
    unpickable: Set<String>,
    onSetLimit: (String) -> Unit,
) {
    val c = LocalClampColors.current
    if (history == null) {
        Txt(
            if (noAccess) S.USAGE_REVOKED else S.NO_USAGE_DATA, ClampType.label,
            if (noAccess) c.red else c.dim, Modifier.padding(vertical = 24.dp),
        )
        return
    }
    var sel by rememberSaveable { mutableIntStateOf(6) }
    val days = history.last7
    val day = days[sel.coerceIn(0, 6)]
    val max = (days.map { it.total } + history.avg7).maxOrNull()?.takeIf { !it.isZero } ?: Duration.ofHours(1)
    val barMax = CHART_H - LABEL_H

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Txt("LAST 7 DAYS · AVG ${dur(history.avg7)}", ClampType.label, c.dim, Modifier.weight(1f, fill = false))
            history.trend?.let { Txt("${trendText(it)} VS PREV 7D", ClampType.label, c.fg, Modifier.padding(start = 8.dp)) }
        }

        // Chart
        Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(CHART_H)) {
            Row(Modifier.fillMaxWidth().height(CHART_H)) {
                days.forEachIndexed { i, d ->
                    val selected = i == sel
                    Column(
                        Modifier.weight(1f).widthIn(min = 48.dp).fillMaxHeight().padding(horizontal = 2.dp)
                            .semantics {
                                contentDescription = "${DESC_FMT.format(d.date)}: ${if (d.hasData) dur(d.total) else "no data"}" + if (d.approx) ", approximate" else ""
                                this.selected = selected
                                stateDescription = if (selected) "Selected" else "Not selected"
                            }
                            .plainClickable(role = Role.Button) { sel = i },
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Txt(
                            if (d.hasData) axisLabel(d.total) else "—",
                            ClampType.label.copy(fontSize = 11.sp, letterSpacing = 0.sp, textAlign = TextAlign.Center),
                            if (selected) c.fg else c.dim, Modifier.height(LABEL_H),
                        )
                        if (!d.hasData) {
                            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                                drawRect(
                                    c.dim,
                                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                                )
                            }
                        } else {
                            val h = maxOf(2f, barMax.value * (d.total.toMillis().toFloat() / max.toMillis())).dp
                            Box(
                                Modifier.fillMaxWidth().height(h)
                                    .background(if (selected) c.fg else Color.Transparent)
                                    .then(if (d.approx) Modifier else Modifier.border(2.dp, c.fg)),
                            ) {
                                if (d.approx) Canvas(Modifier.matchParentSize()) {
                                    drawRect(c.fg, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                                }
                            }
                        }
                    }
                }
            }
            // Dashed AVG line + tiny label
            val avgH = barMax.value * (history.avg7.toMillis().toFloat() / max.toMillis())
            Canvas(Modifier.fillMaxWidth().height(CHART_H)) {
                val y = size.height - avgH.dp.toPx()
                drawLine(
                    c.dim, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)),
                )
            }
            Txt("AVG", ClampType.label.copy(fontSize = 9.sp), c.dim, Modifier.align(Alignment.TopEnd).offset(y = (CHART_H.value - avgH - 14).dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            days.forEachIndexed { i, d ->
                val name = if (i == 6) "TODAY" else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2).uppercase()
                Txt(
                    name, ClampType.label.copy(fontSize = 10.sp, letterSpacing = 0.5.sp, textAlign = TextAlign.Center),
                    if (i == sel) c.fg else c.dim, Modifier.weight(1f),
                )
            }
        }

        // By app
        Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val title = if (sel == 6) "TODAY" else DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(day.date).uppercase()
            Txt("$title · ${if (day.hasData) dur(day.total) else "—"}", ClampType.label, c.fg)
            Txt(S.BY_APP, ClampType.label, c.dim)
        }
        Hairline()
        day.perApp.entries.filter { it.value >= Duration.ofMinutes(1) }.sortedByDescending { it.value }.forEach { (pkg, time) ->
            val target = config.target(pkg)
            val locked = sel == 6 && target != null && (used?.get(pkg) ?: Duration.ZERO) >= Duration.ofMinutes(target.limitMin.toLong())
            AppUsageRow(
                pkg, labels[pkg] ?: pkg, time, target?.limitMin, locked,
                history.apps[pkg], canSetLimit = target == null && pkg !in unpickable, onSetLimit = { onSetLimit(pkg) },
            )
        }
    }
}

@Composable
private fun AppUsageRow(
    pkg: String,
    label: String,
    time: Duration,
    limitMin: Int?,
    locked: Boolean,
    hist: AppHistory?,
    canSetLimit: Boolean,
    onSetLimit: () -> Unit,
) {
    val c = LocalClampColors.current
    val accent = if (locked) c.red else c.fg
    ClampRow {
        AppIcon(pkg, blocked = locked)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(label.uppercase(), ClampType.name, accent, Modifier.weight(1f), maxLines = 1)
                Txt(dur(time), ClampType.num.copy(fontSize = 14.sp), accent)
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    if (limitMin != null) {
                        Box(Modifier.border(2.dp, accent).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Txt(
                                "LIMIT ${durMin(limitMin)}" + if (locked) " · LOCKED" else "",
                                ClampType.label.copy(fontSize = 11.sp), accent,
                            )
                        }
                    } else if (canSetLimit) {
                        Box(
                            Modifier.heightIn(min = 48.dp).plainClickable(role = Role.Button, onClick = onSetLimit),
                            contentAlignment = Alignment.CenterStart,
                        ) { Txt(S.PLUS_SET_LIMIT, ClampType.label, c.dim) }
                    }
                }
                if (hist != null) {
                    Sparkline(hist.last7, accent)
                    val t = hist.trend
                    Box(Modifier.width(52.dp).padding(start = 6.dp)) {
                        if (t != null) {
                            Txt(
                                trendText(t), ClampType.label.copy(fontSize = 11.sp, textAlign = TextAlign.End),
                                if (t.up && limitMin != null) c.red else c.fg, Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Duration>, color: Color, height: Dp = 26.dp) {
    val max = values.maxOrNull()?.takeIf { !it.isZero } ?: Duration.ofMinutes(1)
    Row(Modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { v ->
            val h = maxOf(2f, height.value * (v.toMillis().toFloat() / max.toMillis())).dp
            Box(Modifier.width(7.dp).height(h).background(color))
        }
    }
}
