package app.enough.usage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.enough.Graph
import app.enough.limits.domain.Config
import app.enough.usage.android.LiveUsage
import app.enough.usage.domain.History
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

data class UsageUiState(
    val history: History?,
    val noAccess: Boolean,
    val labels: Map<String, String>,
    val config: Config,
    val used: Map<String, Duration>?,
    val now: Instant,
    /** Apps that may not get a limit (launcher, dialer, Enough itself, ...). */
    val unpickable: Set<String>,
)

class UsageViewModel(graph: Graph, config: StateFlow<Config?>) : ViewModel() {
    private val feed = graph.usageFeed.live

    val uiState: StateFlow<UsageUiState> = combine(config.filterNotNull(), feed, ::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), build(config.value ?: Config(), feed.value))

    companion object {
        fun build(cfg: Config, live: LiveUsage) = UsageUiState(
            history = live.history, noAccess = live.historyNoAccess, labels = live.labels, config = cfg,
            used = live.usage?.usage, now = live.now, unpickable = live.unpickable,
        )
    }
}
