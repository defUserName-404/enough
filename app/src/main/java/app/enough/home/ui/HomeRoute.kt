package app.enough.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enough.limits.domain.Config
import app.enough.limits.domain.Target
import app.enough.rememberGraph
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HomeRoute(
    config: StateFlow<Config?>,
    onEdit: (Target) -> Unit,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUsage: () -> Unit,
) {
    val graph = rememberGraph()
    val vm: HomeViewModel = viewModel { HomeViewModel(graph, config, createSavedStateHandle()) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onEdit = onEdit,
        onAdd = onAdd,
        onCancel = vm::cancel,
        onInstallUpdate = vm::installUpdate,
        onTab = vm::setTab,
        onOpenSettings = onOpenSettings,
        onOpenUsage = onOpenUsage,
    )
}
