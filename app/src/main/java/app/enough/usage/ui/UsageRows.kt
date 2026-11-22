package app.enough.usage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.common.domain.dur
import app.enough.common.domain.durMin
import app.enough.common.ui.S
import app.enough.common.ui.components.AppIcon
import app.enough.common.ui.components.EnoughRow
import app.enough.common.ui.components.LockIcon
import app.enough.common.ui.components.Txt
import app.enough.common.ui.components.plainClickable
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import app.enough.usage.domain.AppHistory
import java.time.Duration

@Composable
internal fun AppUsageRow(
    pkg: String,
    label: String,
    time: Duration,
    limitMin: Int?,
    locked: Boolean,
    hist: AppHistory?,
    canSetLimit: Boolean,
    onSetLimit: () -> Unit,
) {
    val c = LocalEnoughColors.current
    val accent = if (locked) c.red else c.fg
    EnoughRow {
        AppIcon(pkg, blocked = locked)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(label.uppercase(), EnoughType.name, accent, Modifier.weight(1f), maxLines = 1)
                Txt(dur(time), EnoughType.num.copy(fontSize = 14.sp), accent)
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    if (limitMin != null) {
                        Row(Modifier.border(2.dp, accent).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (locked) {
                                LockIcon(accent, 12.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Txt("LIMIT ${durMin(limitMin)}", EnoughType.label.copy(fontSize = 11.sp), accent)
                        }
                    } else if (canSetLimit) {
                        Box(
                            Modifier.heightIn(min = 48.dp).plainClickable(role = Role.Button, onClick = onSetLimit),
                            contentAlignment = Alignment.CenterStart,
                        ) { Txt(S.PLUS_SET_LIMIT, EnoughType.label, c.dim) }
                    }
                }
                if (hist != null) {
                    Sparkline(hist.last7, accent)
                    val t = hist.trend
                    Box(Modifier.width(52.dp).padding(start = 6.dp)) {
                        if (t != null) {
                            Txt(
                                trendText(t), EnoughType.label.copy(fontSize = 11.sp, textAlign = TextAlign.End),
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
internal fun Sparkline(values: List<Duration>, color: Color, height: Dp = 26.dp) {
    val max = values.maxOrNull()?.takeIf { !it.isZero } ?: Duration.ofMinutes(1)
    Row(Modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { v ->
            val h = maxOf(2f, height.value * (v.toMillis().toFloat() / max.toMillis())).dp
            Box(Modifier.width(7.dp).height(h).background(color))
        }
    }
}
