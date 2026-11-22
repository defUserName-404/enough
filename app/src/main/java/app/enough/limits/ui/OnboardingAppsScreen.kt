package app.enough.limits.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.enough.common.ui.S
import app.enough.common.ui.components.AppIcon
import app.enough.common.ui.components.BtnStyle
import app.enough.common.ui.components.EnoughButton
import app.enough.common.ui.components.EnoughCheckBox
import app.enough.common.ui.components.EnoughRow
import app.enough.common.ui.components.Hairline
import app.enough.common.ui.components.InvertBox
import app.enough.common.ui.components.SearchField
import app.enough.common.ui.components.Stepper
import app.enough.common.ui.components.Txt
import app.enough.common.ui.theme.Dims
import app.enough.common.ui.theme.EnoughType
import app.enough.common.ui.theme.LocalEnoughColors
import app.enough.limits.domain.Target
import app.enough.usage.domain.AppInfo
import app.enough.usage.domain.PickerFilter

enum class PickerMode { SETUP, ADD }

private val selectionSaver = listSaver<SnapshotStateMap<String, Int>, String>(
    save = { m -> m.map { (k, v) -> "$k|$v" } },
    restore = { l ->
        mutableStateMapOf<String, Int>().apply {
            l.forEach { s ->
                val i = s.lastIndexOf('|')
                put(s.substring(0, i), s.substring(i + 1).toInt())
            }
        }
    },
)

@Composable
fun OnboardingAppsScreen(
    state: PickerUiState,
    onDone: (List<Target>) -> Unit,
    onBack: (() -> Unit)?,
) {
    val mode = state.mode
    val initial = state.initial
    val apps = state.apps
    val c = LocalEnoughColors.current
    var query by rememberSaveable { mutableStateOf("") }
    val sel = rememberSaveable(saver = selectionSaver) {
        mutableStateMapOf<String, Int>().apply { initial.forEach { put(it.pkg, it.limitMin) } }
    }
    val visible = remember(apps, query) { PickerFilter.visible(apps, emptySet(), query) }
    val total = sel.values.sum()

    Column(Modifier.fillMaxSize().padding(horizontal = Dims.Pad)) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(
                            if (mode == PickerMode.SETUP) S.SETUP_1 else S.ADD_APP,
                            EnoughType.label, c.fg, Modifier.weight(1f),
                        )
                        if (onBack != null) {
                            InvertBox(onBack, Modifier.size(Dims.Touch), desc = "Close") { Txt("✕", EnoughType.num, it) }
                        }
                    }
                    if (mode == PickerMode.SETUP) {
                        Txt(S.APPS_TITLE, EnoughType.h1, c.fg, Modifier.padding(top = 14.dp))
                        Txt(S.APPS_BODY, EnoughType.body, c.fg, Modifier.padding(top = 14.dp))
                    } else {
                        Txt(S.ADD_APP, EnoughType.h2, c.fg, Modifier.padding(top = 6.dp))
                    }
                    Box(Modifier.padding(top = 22.dp, bottom = 18.dp)) {
                        SearchField(query) { query = it }
                    }
                }
            }
            items(visible, key = { it.pkg }) { app ->
                val checked = app.pkg in sel
                EnoughRow(
                    Modifier.semantics {
                        role = Role.Checkbox
                        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                    },
                    onClick = { if (checked) sel.remove(app.pkg) else sel[app.pkg] = 20 },
                ) {
                    EnoughCheckBox(checked)
                    AppIcon(app.pkg)
                    Column(Modifier.weight(1f)) {
                        Txt(app.label.uppercase(), EnoughType.name, if (checked) c.fg else c.dim, maxLines = 1)
                        if (checked) Txt(S.MIN_DAY, EnoughType.label, c.dim, Modifier.padding(top = 4.dp))
                    }
                    if (checked) Stepper(sel[app.pkg] ?: 20, { sel[app.pkg] = it })
                }
            }
        }
        Hairline(thick = true)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Txt("${sel.size} APPS", EnoughType.label)
            Txt("$total ${S.MIN_DAY}", EnoughType.label)
        }
        EnoughButton(
            if (mode == PickerMode.SETUP) S.NEXT else S.ADD,
            onClick = {
                val ordered = apps.filter { it.pkg in sel }.map { Target(it.pkg, sel[it.pkg]!!) }
                val missing = sel.keys.filter { k -> apps.none { it.pkg == k } }.map { Target(it, sel[it]!!) }
                onDone(ordered + missing)
            },
            modifier = Modifier.fillMaxWidth(),
            style = BtnStyle.PRIMARY,
            enabled = sel.isNotEmpty(),
        )
        Box(Modifier.size(16.dp))
    }
}
