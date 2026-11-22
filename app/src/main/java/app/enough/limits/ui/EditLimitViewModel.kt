package app.enough.limits.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.enough.EditTarget
import app.enough.Graph
import app.enough.limits.domain.Change
import app.enough.limits.domain.ChangePolicy
import app.enough.limits.domain.Config
import app.enough.limits.domain.PendingChange
import app.enough.limits.domain.Target
import app.enough.usage.android.LiveUsage
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditLimitUiState(
    val target: Target,
    val label: String,
    val isNew: Boolean,
    /** The pending (delayed) change for this app, if any. */
    val pending: PendingChange?,
    val now: Instant,
)

/** [editing]: the open sheet, null when closed. */
class EditLimitViewModel(
    private val graph: Graph,
    config: StateFlow<Config?>,
    editing: StateFlow<EditTarget?>,
) : ViewModel() {
    private val feed = graph.usageFeed.live

    val uiState: StateFlow<EditLimitUiState?> = combine(editing, config, feed) { e, c, l -> build(e, c, l) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), build(editing.value, config.value, feed.value))

    fun submit(pkg: String, minutes: Int) { viewModelScope.launch { graph.controller.setLimit(pkg, minutes) } }
    fun remove(pkg: String) { viewModelScope.launch { graph.controller.submit(Change.Remove(pkg)) } }

    companion object {
        fun build(editing: EditTarget?, cfg: Config?, live: LiveUsage): EditLimitUiState? {
            val e = editing ?: return null
            val t = e.target
            cfg ?: return null
            val key = "pkg:${t.pkg}"
            return EditLimitUiState(
                target = cfg.target(t.pkg) ?: t,
                label = live.labels[t.pkg] ?: t.pkg,
                isNew = e.isNew,
                pending = cfg.pending.firstOrNull { ChangePolicy.keyOf(it.change) == key },
                now = live.now,
            )
        }
    }
}
