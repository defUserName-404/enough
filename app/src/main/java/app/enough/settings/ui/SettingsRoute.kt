package app.enough.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enough.limits.domain.Config
import app.enough.rememberGraph
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SettingsRoute(config: StateFlow<Config?>, onBack: () -> Unit) {
    val graph = rememberGraph()
    val vm: SettingsViewModel = viewModel { SettingsViewModel(graph, config) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onCheckUpdate = vm::checkUpdate,
        onInstallUpdate = vm::installUpdate,
        onRelease = vm::release,
        onBack = onBack,
    )
}
