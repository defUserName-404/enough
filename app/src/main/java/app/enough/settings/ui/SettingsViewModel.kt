package app.enough.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.enough.Graph
import app.enough.limits.domain.Change
import app.enough.limits.domain.Config
import app.enough.update.domain.ReleaseInfo
import app.enough.update.domain.UpdateState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val config: Config, val updateState: UpdateState)

class SettingsViewModel(private val graph: Graph, config: StateFlow<Config?>) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(config.filterNotNull(), graph.updater.state, ::SettingsUiState)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(1_000),
            SettingsUiState(config.value ?: Config(), graph.updater.state.value),
        )

    fun checkUpdate() { viewModelScope.launch { graph.updater.check() } }
    fun installUpdate(info: ReleaseInfo) { viewModelScope.launch { graph.updater.install(info) } }

    /** Queues [Change.Release]; it loosens, so it waits [app.enough.limits.domain.ChangePolicy.DELAY]. */
    fun release() { viewModelScope.launch { graph.controller.submit(Change.Release) } }
}
