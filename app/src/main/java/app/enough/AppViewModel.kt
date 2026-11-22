package app.enough

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.enough.limits.domain.Config
import app.enough.limits.domain.Phase
import app.enough.limits.domain.Target
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { APPS, LOCK, HOME, ADD, SETTINGS, USAGE }

/** The limit sheet: [isNew] = opened from Usage for an app without a limit yet. */
data class EditTarget(val target: Target, val isNew: Boolean)

sealed interface AppUiState {
    data object Loading : AppUiState
    data object LoadFailed : AppUiState
    data class Releasing(val failed: Boolean) : AppUiState
    data class Ready(val config: Config, val screen: Screen, val editing: EditTarget?) : AppUiState
}

/**
 * Root state: loads config, picks the screen from the phase, and runs the phase side effects
 * (release retry, starting the service, detecting device owner during setup).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val graph: Graph, private val saved: SavedStateHandle) : ViewModel() {
    private val controller = graph.controller
    private val loadFailed = MutableStateFlow(false)
    private val _resumeTick = MutableStateFlow(0)
    private val _notifDenied = MutableStateFlow(false)
    private val setupStep = saved.getStateFlow<Screen?>(K_SETUP_STEP, null)
    private val adding = saved.getStateFlow(K_ADDING, false)
    private val homeSub = saved.getStateFlow<Screen?>(K_HOME_SUB, null) // SETTINGS / USAGE over Home
    private val _editing = MutableStateFlow<EditTarget?>(null)

    /** Bumped on every Activity resume: re-check usage access and device owner. */
    val resumeTick: StateFlow<Int> = _resumeTick.asStateFlow()
    val notifDenied: StateFlow<Boolean> = _notifDenied.asStateFlow()
    val editing: StateFlow<EditTarget?> = _editing.asStateFlow()

    val config: StateFlow<Config?> = graph.store.flow
        .catch { Log.e("Enough", "config unreadable", it); loadFailed.value = true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<AppUiState> = combine(
        combine(config, loadFailed, ::Pair),
        combine(setupStep, adding, homeSub, ::Triple),
        _editing,
    ) { (cfg, failed), (step, add, sub), edit -> toUiState(cfg, failed, step, add, sub, edit) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState.Loading)

    init {
        viewModelScope.launch {
            combine(config.filterNotNull().map { it.phase }, _resumeTick, graph.setup.view.map { it.state }, ::Triple)
                .distinctUntilChanged()
                .collectLatest { (phase) -> onPhase(phase) }
        }
        // A new-limit sheet closes itself if the target appears externally.
        viewModelScope.launch {
            combine(_editing, config.filterNotNull()) { e, cfg -> e != null && e.isNew && cfg.target(e.target.pkg) != null }
                .collect { appeared -> if (appeared) _editing.value = null }
        }
        // Silent update check once per launch, the first time the Home area shows.
        viewModelScope.launch {
            if (saved.get<Boolean>(K_LAUNCH_CHECKED) == true) return@launch
            uiState.first { it is AppUiState.Ready && it.screen in HOME_AREA }
            saved[K_LAUNCH_CHECKED] = true
            graph.updater.check()
        }
    }

    private suspend fun onPhase(phase: Phase) {
        when (phase) {
            Phase.RELEASING -> while (!controller.runRelease()) delay(5_000)
            Phase.ACTIVE -> { saved[K_SETUP_STEP] = null; controller.startService() }
            Phase.SETUP -> {
                saved[K_ADDING] = false; _editing.value = null; saved[K_HOME_SUB] = null
                if (graph.policy.isActive()) {
                    // Same gate as the lock screen: never while adb-off is pending.
                    while (!graph.setup.canActivate()) delay(1_000)
                    controller.onDeviceOwnerDetected()
                }
            }
        }
    }

    // --- events ---
    fun onResume() = _resumeTick.update { it + 1 }
    fun setNotifDenied(denied: Boolean) { _notifDenied.value = denied }
    fun setupTargetsSaved() { saved[K_SETUP_STEP] = Screen.LOCK }
    fun backToApps() { saved[K_SETUP_STEP] = Screen.APPS }
    fun openAdd() { saved[K_ADDING] = true }
    fun closeAdd() { saved[K_ADDING] = false }
    fun openSettings() { saved[K_HOME_SUB] = Screen.SETTINGS }
    fun openUsage() { saved[K_HOME_SUB] = Screen.USAGE }
    fun closeSub() { saved[K_HOME_SUB] = null }
    fun edit(target: Target, isNew: Boolean) { _editing.value = EditTarget(target, isNew) }
    fun closeEditor() { _editing.value = null }

    companion object {
        private const val K_SETUP_STEP = "setupStep"
        private const val K_ADDING = "adding"
        private const val K_HOME_SUB = "homeSub"
        private const val K_LAUNCH_CHECKED = "launchChecked"
        val HOME_AREA = setOf(Screen.HOME, Screen.SETTINGS, Screen.USAGE)

        /** Pure: config + navigation state -> what to show. */
        fun toUiState(
            cfg: Config?, failed: Boolean, step: Screen?, adding: Boolean, homeSub: Screen?, editing: EditTarget?,
        ): AppUiState = when {
            failed -> AppUiState.LoadFailed
            cfg == null -> AppUiState.Loading
            cfg.phase == Phase.RELEASING -> AppUiState.Releasing(cfg.releaseFailed)
            else -> {
                val screen = when (cfg.phase) {
                    Phase.ACTIVE -> if (adding) Screen.ADD else homeSub ?: Screen.HOME
                    else -> step ?: if (cfg.targets.isEmpty()) Screen.APPS else Screen.LOCK
                }
                AppUiState.Ready(cfg, screen, editing?.takeIf { screen in HOME_AREA })
            }
        }

        fun factory(graph: Graph): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(graph, createSavedStateHandle()) }
        }
    }
}
