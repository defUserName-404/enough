package app.enough.usage.android

import app.enough.enforce.domain.Enforcer
import app.enough.limits.domain.Config
import app.enough.usage.domain.History
import app.enough.usage.domain.UsageSnapshot
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Live data shared by Home, Usage and the edit sheet. */
data class LiveUsage(
    val usage: UsageSnapshot? = UsageSnapshot(emptyMap(), null),
    val now: Instant = Instant.now(),
    val history: History? = null,
    val historyNoAccess: Boolean = false,
    /** History labels + target labels. */
    val labels: Map<String, String> = emptyMap(),
    val unpickable: Set<String> = emptySet(),
)

/**
 * Polls usage every 5 s and history every 60 s, but only while someone collects [live]
 * (the UI collects only while STARTED). A restart, e.g. on return to the app, polls at once.
 * The last good values are kept across restarts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UsageFeed(
    private val reader: UsageReader,
    private val historyReader: HistoryReader,
    private val catalog: AppCatalog,
    config: Flow<Config>,
    scope: CoroutineScope,
) {
    private data class Poll(
        val at: Instant,
        val usage: UsageSnapshot?,
        val history: History?,
        val historyNoAccess: Boolean,
        val historyLabels: Map<String, String>,
    )

    @Volatile private var last = Poll(Instant.now(), UsageSnapshot(emptyMap(), null), null, false, emptyMap())

    private val keep = SharingStarted.WhileSubscribed(STOP_MS)

    private val poll: StateFlow<Poll> = flow {
        var n = 0
        while (true) {
            val clock = Clock.systemDefaultZone()
            val usage = withContext(Dispatchers.IO) { reader.read(Enforcer(clock).dayStart(), clock.instant()) }
            var p = last.copy(at = Instant.now(), usage = usage)
            // History is heavier: reload on start and every 60 s (12 x 5 s). Keep the last good value.
            if (n++ % 12 == 0) {
                p = when (val r = historyReader.load()) {
                    is HistoryResult.Ok -> p.copy(history = r.history, historyLabels = r.labels, historyNoAccess = false)
                    HistoryResult.NoAccess -> p.copy(history = null, historyNoAccess = true)
                    HistoryResult.Error -> p
                }
            }
            last = p
            emit(p)
            delay(5_000)
        }
    }.stateIn(scope, keep, last)

    /** Minute precision for countdowns between polls. */
    private val tick: StateFlow<Instant> = flow {
        while (true) { delay(30_000); emit(Instant.now()) }
    }.stateIn(scope, keep, Instant.now())

    private val safeConfig = config.catch { }

    // Binder calls (labels, default launcher/dialer lookups) stay off the main thread.
    private val targetLabels: StateFlow<Map<String, String>> = safeConfig
        .map { it.targets }
        .distinctUntilChanged()
        .mapLatest { ts -> withContext(Dispatchers.IO) { ts.associate { it.pkg to catalog.label(it.pkg) } } }
        .stateIn(scope, keep, emptyMap())

    private val unpickable: StateFlow<Set<String>> = safeConfig
        .distinctUntilChangedBy { it.unsuspendable }
        .mapLatest { cfg -> withContext(Dispatchers.IO) { catalog.unpickable(cfg) } }
        .stateIn(scope, keep, emptySet())

    private fun build(p: Poll, labels: Map<String, String>, unpickable: Set<String>) = LiveUsage(
        usage = p.usage,
        now = Instant.now(),
        history = p.history,
        historyNoAccess = p.historyNoAccess,
        labels = p.historyLabels + labels,
        unpickable = unpickable,
    )

    val live: StateFlow<LiveUsage> = combine(poll, tick, targetLabels, unpickable) { p, _, l, u -> build(p, l, u) }
        .stateIn(scope, keep, build(last, emptyMap(), emptySet()))

    private companion object {
        /** Survives switching between Home, Usage and Settings; stops soon after the app leaves the screen. */
        const val STOP_MS = 1_000L
    }
}
