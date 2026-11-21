package dev.clamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.clamp.ui.components.Txt
import dev.clamp.ui.theme.ClampType
import dev.clamp.ui.theme.Dims
import dev.clamp.ui.theme.LocalClampColors

@Composable
fun ReleasingScreen(failed: Boolean) {
    val c = LocalClampColors.current
    Column(Modifier.fillMaxSize().padding(Dims.Pad), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Txt(if (failed) S.RELEASE_FAILED else S.RELEASING, ClampType.h2, if (failed) c.red else c.fg)
    }
}

@Composable
fun ErrorScreen() {
    val c = LocalClampColors.current
    Column(Modifier.fillMaxSize().padding(Dims.Pad), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Txt(S.CONFIG_ERROR, ClampType.h2, c.red)
        Txt(S.CONFIG_ERROR_BODY, ClampType.body, c.fg, Modifier.padding(top = 12.dp))
    }
}
