package app.enough.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.BuildConfig
import app.enough.common.domain.durMin
import app.enough.common.domain.left
import app.enough.common.ui.S
import app.enough.common.ui.components.BtnStyle
import app.enough.common.ui.components.EnoughBox
import app.enough.common.ui.components.EnoughButton
import app.enough.common.ui.components.ProgressRing
import app.enough.common.ui.components.Txt
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import app.enough.limits.domain.Change
import app.enough.limits.domain.Config
import app.enough.limits.domain.PendingChange
import app.enough.update.domain.ReleaseInfo
import app.enough.update.domain.UpdateState
import java.time.Duration
import java.time.Instant

@Composable
internal fun PendingRow(
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
internal fun UpdateBanner(state: UpdateState, onInstall: (ReleaseInfo) -> Unit) {
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
