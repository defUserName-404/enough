package app.enough.setup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.common.ui.S
import app.enough.common.ui.components.InvertBox
import app.enough.common.ui.components.Txt
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors

@Composable
internal fun ComputerTab(usageGranted: Boolean) {
    val c = LocalEnoughColors.current
    val clip = LocalClipboardManager.current
    Txt(S.RUN_ON_COMPUTER, EnoughType.label, c.fg, Modifier.padding(top = 22.dp))
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(c.fg).border(2.dp, c.fg).padding(16.dp)) {
        Txt(S.ADB_DISPLAY, EnoughType.num.copy(fontSize = 12.5.sp), c.bg)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            InvertBox(
                { clip.setText(AnnotatedString(S.ADB_CMD)) },
                Modifier.border(2.dp, c.bg).padding(horizontal = 10.dp, vertical = 6.dp),
                desc = "Copy command",
            ) { Txt(S.COPY, EnoughType.label.copy(fontSize = 11.sp), c.bg) }
        }
    }
    Column(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
        listOf(S.STEP_1, S.STEP_2, S.STEP_3).forEach {
            Txt(it, EnoughType.label.copy(fontSize = 11.5.sp, lineHeight = 20.sp), c.dim)
        }
    }
}
