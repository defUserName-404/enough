package app.enough.limits.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enough.limits.domain.Config
import app.enough.rememberGraph

@Composable
fun PickerRoute(mode: PickerMode, config: Config, onDone: () -> Unit, onBack: (() -> Unit)?) {
    val graph = rememberGraph()
    val vm: PickerViewModel = viewModel { PickerViewModel(graph) }
    val apps by vm.apps.collectAsStateWithLifecycle()
    val state = remember(mode, config.targets, apps) { PickerViewModel.uiState(mode, config, apps) }
    OnboardingAppsScreen(state, onDone = { picked -> vm.submit(mode, picked, onDone) }, onBack = onBack)
}
