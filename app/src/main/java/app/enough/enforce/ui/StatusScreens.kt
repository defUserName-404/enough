package app.enough.enforce.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.enough.common.ui.S
import app.enough.common.ui.components.Txt
import app.enough.common.ui.theme.Dims
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors

@Composable
fun ReleasingScreen(failed: Boolean) {
    val c = LocalEnoughColors.current
    Column(Modifier.fillMaxSize().padding(Dims.Pad), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Txt(if (failed) S.RELEASE_FAILED else S.RELEASING, EnoughType.h2, if (failed) c.red else c.fg)
        Txt(S.RELEASING_BODY, EnoughType.body, c.fg, Modifier.padding(top = 12.dp))
    }
}

@Composable
fun ErrorScreen() {
    val c = LocalEnoughColors.current
    Column(Modifier.fillMaxSize().padding(Dims.Pad), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Txt(S.CONFIG_ERROR, EnoughType.h2, c.red)
        Txt(S.CONFIG_ERROR_BODY, EnoughType.body, c.fg, Modifier.padding(top = 12.dp))
    }
}
