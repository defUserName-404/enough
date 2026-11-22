package app.enough.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enough.common.ui.S
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors

@Composable
fun EnoughCheckBox(checked: Boolean, modifier: Modifier = Modifier) {
    val c = LocalEnoughColors.current
    Box(
        modifier.size(24.dp).border(2.dp, c.fg).background(if (checked) c.fg else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Txt("✓", EnoughType.label.copy(fontSize = 14.sp), c.bg)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = LocalEnoughColors.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = EnoughType.num.copy(color = c.fg, textAlign = TextAlign.Start),
        cursorBrush = SolidColor(c.fg),
        modifier = Modifier.fillMaxWidth().height(52.dp).border(2.dp, c.fg).padding(horizontal = 14.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Txt(S.SEARCH, EnoughType.num, c.dim)
                inner()
            }
        },
    )
}

/** Two-or-more cell tab switcher; the selected cell is filled. */
@Composable
fun EnoughTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalEnoughColors.current
    val tick = hapticClick(onClick = {})
    Row(modifier.fillMaxWidth().border(2.dp, c.fg)) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).background(if (sel) c.fg else Color.Transparent)
                    .plainClickable(role = Role.Tab) { tick(); onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Txt(label, EnoughType.label.copy(fontSize = 12.sp), if (sel) c.bg else c.fg, Modifier.padding(8.dp))
            }
        }
    }
}
