package app.enough.limits.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enough.EditTarget
import app.enough.limits.domain.Config
import app.enough.rememberGraph
import kotlinx.coroutines.flow.StateFlow

@Composable
fun EditLimitRoute(config: StateFlow<Config?>, editing: StateFlow<EditTarget?>, onClose: () -> Unit) {
    val graph = rememberGraph()
    val vm: EditLimitViewModel = viewModel { EditLimitViewModel(graph, config, editing) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val s = state ?: return
    EditLimitSheet(
        state = s,
        onSubmit = { m -> vm.submit(s.target.pkg, m); onClose() },
        onRemove = { vm.remove(s.target.pkg); onClose() },
        onClose = onClose,
    )
}
