package app.enough.setup.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enough.oem.android.OemItem
import app.enough.rememberGraph
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SetupRoute(
    resumeTick: StateFlow<Int>,
    notifDenied: StateFlow<Boolean>,
    onUsage: () -> Unit,
    onLaunchOem: (OemItem) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenDevSettings: () -> Unit,
    onPair: () -> Unit,
    onBack: () -> Unit,
) {
    val graph = rememberGraph()
    val vm: SetupViewModel = viewModel { SetupViewModel(graph, resumeTick, notifDenied, createSavedStateHandle()) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.usageGranted) {
        if (state.usageGranted) vm.awaitActivation()
    }

    OnboardingLockScreen(
        state = state,
        onUsage = onUsage,
        onOem = { spec -> vm.openOem(spec)?.let(onLaunchOem) },
        onAccountsToggle = vm::toggleAccounts,
        onOpenAccounts = onOpenAccounts,
        onOpenDevSettings = onOpenDevSettings,
        onPair = onPair,
        onCancelSetup = vm::cancelSetup,
        onBack = onBack,
    )
}
