package app.enough.limits.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.enough.Graph
import app.enough.limits.domain.Config
import app.enough.limits.domain.Target
import app.enough.usage.domain.AppInfo
import app.enough.usage.domain.PickerFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PickerUiState(val mode: PickerMode, val initial: List<Target>, val apps: List<AppInfo>)

/** App picker for setup (choose apps) and Home's ADD. */
@OptIn(ExperimentalCoroutinesApi::class)
class PickerViewModel(private val graph: Graph) : ViewModel() {
    /** Loaded on IO while a picker is on screen; reloads when the unsuspendable set changes. */
    val apps: StateFlow<List<AppInfo>> = graph.store.flow
        .catch { }
        .distinctUntilChangedBy { it.unsuspendable }
        .mapLatest { cfg ->
            withContext(Dispatchers.IO) { PickerFilter.visible(graph.catalog.launchable(), graph.catalog.unpickable(cfg), "") }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun submit(mode: PickerMode, picked: List<Target>, onDone: () -> Unit) {
        viewModelScope.launch {
            when (mode) {
                PickerMode.SETUP -> graph.controller.saveSetupTargets(picked)
                PickerMode.ADD -> picked.forEach { graph.controller.setLimit(it.pkg, it.limitMin) }
            }
            onDone()
        }
    }

    companion object {
        /** SETUP starts from the saved targets; ADD offers only apps without a limit. */
        fun uiState(mode: PickerMode, cfg: Config, apps: List<AppInfo>): PickerUiState = when (mode) {
            PickerMode.SETUP -> PickerUiState(mode, cfg.targets, apps)
            PickerMode.ADD -> {
                val taken = cfg.targets.map { it.pkg }.toSet()
                PickerUiState(mode, emptyList(), apps.filter { it.pkg !in taken })
            }
        }
    }
}
