package app.enough.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.enough.common.ui.theme.LocalEnoughColors

@Composable
fun EnoughRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalEnoughColors.current
    val base = if (onClick != null) modifier.plainClickable(role = Role.Button, onClick = onClick) else modifier
    Column(base.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun EnoughBox(red: Boolean = false, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalEnoughColors.current
    Column(modifier.fillMaxWidth().border(2.dp, if (red) c.red else c.fg).padding(16.dp), content = content)
}

@Composable
fun Hairline(thick: Boolean = false) {
    val c = LocalEnoughColors.current
    Box(
        Modifier.fillMaxWidth().height(if (thick) 2.dp else 1.dp).background(if (thick) c.fg else c.line),
    )
}

@Composable
fun Scrim(onDismiss: () -> Unit) {
    val c = LocalEnoughColors.current
    Box(Modifier.fillMaxSize().background(c.scrim).plainClickable(onClick = onDismiss))
}
