package app.enough.usage.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enough.limits.domain.Config
import app.enough.rememberGraph
import kotlinx.coroutines.flow.StateFlow

@Composable
fun UsageRoute(config: StateFlow<Config?>, onSetLimit: (String) -> Unit, onBack: () -> Unit) {
    val graph = rememberGraph()
    val vm: UsageViewModel = viewModel { UsageViewModel(graph, config) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    UsageScreen(state = state, onSetLimit = onSetLimit, onBack = onBack)
}
