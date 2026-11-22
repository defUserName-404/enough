package app.enough

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.enough.enforce.ui.ErrorScreen
import app.enough.enforce.ui.ReleasingScreen
import app.enough.home.ui.HomeRoute
import app.enough.limits.domain.Target
import app.enough.limits.ui.EditLimitRoute
import app.enough.limits.ui.PickerMode
import app.enough.limits.ui.PickerRoute
import app.enough.oem.android.OemItem
import app.enough.settings.ui.SettingsRoute
import app.enough.setup.ui.SetupRoute
import app.enough.usage.ui.UsageRoute

@Composable
fun rememberGraph(): Graph {
    val ctx = LocalContext.current
    return remember { (ctx.applicationContext as EnoughApp).graph }
}

/** What only the Activity can do (start activities, ask for permissions). */
class ActivityActions(
    val openSettings: (action: String) -> Unit,
    val openUsageAccess: () -> Unit,
    val pair: () -> Unit,
    val launchOem: (OemItem) -> Unit,
)

@Composable
fun AppRoot(vm: AppViewModel, actions: ActivityActions) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        AppUiState.Loading -> Unit
        AppUiState.LoadFailed -> ErrorScreen()
        is AppUiState.Releasing -> ReleasingScreen(s.failed)
        is AppUiState.Ready -> Ready(vm, s, actions)
    }
}

@Composable
private fun Ready(vm: AppViewModel, s: AppUiState.Ready, actions: ActivityActions) {
    val screen = s.screen
    BackHandler(enabled = s.editing != null) { vm.closeEditor() }
    BackHandler(enabled = screen == Screen.LOCK) { vm.backToApps() }
    BackHandler(enabled = screen == Screen.ADD) { vm.closeAdd() }
    BackHandler(enabled = screen == Screen.SETTINGS || screen == Screen.USAGE) { vm.closeSub() }

    when (screen) {
        Screen.APPS -> PickerRoute(PickerMode.SETUP, s.config, onDone = vm::setupTargetsSaved, onBack = null)
        Screen.LOCK -> SetupRoute(
            resumeTick = vm.resumeTick,
            notifDenied = vm.notifDenied,
            onUsage = actions.openUsageAccess,
            onLaunchOem = actions.launchOem,
            onOpenAccounts = { actions.openSettings(Settings.ACTION_SYNC_SETTINGS) },
            onOpenDevSettings = { actions.openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
            onPair = actions.pair,
            onBack = vm::backToApps,
        )
        Screen.ADD -> PickerRoute(PickerMode.ADD, s.config, onDone = vm::closeAdd, onBack = vm::closeAdd)
        Screen.SETTINGS -> SettingsRoute(vm.config, onBack = vm::closeSub)
        Screen.USAGE -> UsageRoute(vm.config, onSetLimit = { pkg -> vm.edit(Target(pkg, 30), isNew = true) }, onBack = vm::closeSub)
        Screen.HOME -> HomeRoute(
            config = vm.config,
            onEdit = { vm.edit(it, isNew = false) },
            onAdd = vm::openAdd,
            onOpenSettings = vm::openSettings,
            onOpenUsage = vm::openUsage,
        )
    }
    if (s.editing != null) EditLimitRoute(vm.config, vm.editing, onClose = vm::closeEditor)
}
