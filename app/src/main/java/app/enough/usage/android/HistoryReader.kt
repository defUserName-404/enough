package app.enough.usage.android

import app.enough.usage.domain.History
import app.enough.usage.domain.UsageHistory
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface HistoryResult {
    /** [labels] are resolved here, off the main thread, for every app in the history. */
    data class Ok(val history: History, val labels: Map<String, String>) : HistoryResult
    data object NoAccess : HistoryResult
    data object Error : HistoryResult
}

/** 14+1-day history for the UI. Not used by the Ticker. */
class HistoryReader(private val reader: UsageReader, private val catalog: AppCatalog) {
    suspend fun load(): HistoryResult = withContext(Dispatchers.IO) {
        try {
            if (!reader.hasAccess()) return@withContext HistoryResult.NoAccess
            val clock = Clock.systemDefaultZone()
            val now = clock.instant()
            val today = LocalDate.now(clock)
            val windowStart = today.minusDays(UsageHistory.DAYS - 1L).atStartOfDay(clock.zone).toInstant()
            // Extra 24h so sessions opened before the window are still seen (folding clips to the window).
            val events = reader.events(windowStart.minus(Duration.ofHours(24)), now)
            val daily = runCatching { reader.dailyVisible(windowStart, now, clock.zone) }.getOrDefault(emptyMap())
            val counted = catalog.counted()
            val h = UsageHistory.build(events, today, now, clock.zone, { it in counted }, daily)
            HistoryResult.Ok(h, h.apps.keys.associateWith { catalog.label(it) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HistoryResult.Error
        }
    }
}
