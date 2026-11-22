package app.enough.home.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.enough.Graph
import app.enough.limits.domain.Config
import app.enough.update.domain.ReleaseInfo
import app.enough.update.domain.UpdateState
import app.enough.usage.android.LiveUsage
import app.enough.usage.domain.History
import app.enough.usage.domain.UsageSnapshot
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val config: Config,
    val usage: UsageSnapshot?,
    val labels: Map<String, String>,
    val now: Instant,
    val updateState: UpdateState,
    val history: History?,
    val tab: Int,
)

class HomeViewModel(private val graph: Graph, config: StateFlow<Config?>, private val saved: SavedStateHandle) : ViewModel() {
    private val tab = saved.getStateFlow(K_TAB, 0)
    private val feed = graph.usageFeed.live

    val uiState: StateFlow<HomeUiState> = combine(config.filterNotNull(), feed, graph.updater.state, tab, ::build)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(1_000),
            build(config.value ?: Config(), feed.value, graph.updater.state.value, tab.value),
        )

    fun setTab(i: Int) { saved[K_TAB] = i }
    fun cancel(id: String) { viewModelScope.launch { graph.controller.cancel(id) } }
    fun installUpdate(info: ReleaseInfo) { viewModelScope.launch { graph.updater.install(info) } }

    companion object {
        private const val K_TAB = "homeTab"

        fun build(cfg: Config, live: LiveUsage, update: UpdateState, tab: Int) = HomeUiState(
            config = cfg, usage = live.usage, labels = live.labels, now = live.now,
            updateState = update, history = live.history, tab = tab,
        )
    }
}
