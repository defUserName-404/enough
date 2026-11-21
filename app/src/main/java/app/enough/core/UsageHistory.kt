package app.enough.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** [pct] is the absolute change (>= 1); [up] its direction. */
data class Trend(val pct: Int, val up: Boolean)

data class DayUsage(
    val date: LocalDate,
    val hasData: Boolean,
    val perApp: Map<String, Duration>,
    /** UNION of intervals across counted apps (split screen is not double-counted). For approx days: sum of apps. */
    val total: Duration,
    /** True when taken from the OS daily aggregates because the events were pruned. */
    val approx: Boolean = false,
)

data class AppHistory(val pkg: String, val last7: List<Duration>, val trend: Trend?)

/**
 * 15 days, oldest first. days[14] is today (partial). The chart shows the last 7 (incl. today).
 * AVG and trends use only FULL days: [full7] = yesterday back to 7 days ago, compared with [prev7] (the 7 days before).
 */
data class History(
    val days: List<DayUsage>,
    val avg7: Duration,
    val trend: Trend?,
    val apps: Map<String, AppHistory>,
) {
    val last7: List<DayUsage> get() = days.takeLast(7)
    val full7: List<DayUsage> get() = days.subList(days.size - 8, days.size - 1)
    val prev7: List<DayUsage> get() = days.subList(days.size - 15, days.size - 8)
    val today: DayUsage get() = days.last()
}

object UsageHistory {
    const val DAYS = 15
    /** A single session without a closing event is capped here (history only, never enforcement). */
    val OPEN_CAP: Duration = Duration.ofHours(6)

    private fun merge(list: List<Pair<Instant, Instant>>): List<Pair<Instant, Instant>> {
        val out = ArrayList<Pair<Instant, Instant>>()
        for ((a, b) in list.filter { !it.second.isBefore(it.first) }.sortedBy { it.first }) {
            val last = out.lastOrNull()
            if (last != null && !a.isAfter(last.second)) {
                if (b.isAfter(last.second)) out[out.size - 1] = last.first to b
            } else out += a to b
        }
        return out
    }

    private fun sum(list: List<Pair<Instant, Instant>>): Duration =
        merge(list).fold(Duration.ZERO) { acc, (a, b) -> acc + Duration.between(a, b) }

    /** Null for a zero previous total or a change that rounds to under 1%. */
    fun trend(last: Duration, prev: Duration): Trend? {
        if (prev.isZero) return null
        val pct = (abs((last - prev).toMillis().toDouble()) / prev.toMillis() * 100).roundToInt()
        return if (pct < 1) null else Trend(pct, last > prev)
    }

    /**
     * Fills days that have no events (pruned by Android) from the OS daily aggregates. Counted apps only;
     * the day total is the sum of apps (approx). Today is never filled.
     */
    fun mergeDaily(
        days: List<DayUsage>,
        daily: Map<LocalDate, Map<String, Duration>>,
        today: LocalDate,
        isCounted: (String) -> Boolean,
    ): List<DayUsage> = days.map { d ->
        if (d.hasData || d.date == today) return@map d
        val apps = (daily[d.date] ?: return@map d).filterKeys(isCounted).filterValues { !it.isZero }
        if (apps.isEmpty()) d
        else d.copy(hasData = true, approx = true, perApp = apps, total = apps.values.fold(Duration.ZERO, Duration::plus))
    }

    /**
     * Non-launchable packages (e.g. the in-call UI) are excluded by design via [isCounted].
     */
    fun build(
        events: List<UsageEvt>,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
        isCounted: (String) -> Boolean,
        daily: Map<LocalDate, Map<String, Duration>> = emptyMap(),
    ): History {
        val dates = (DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
        fun start(d: LocalDate) = d.atStartOfDay(zone).toInstant()
        val windowStart = start(dates.first())
        val raw = UsageFolder.intervals(events, windowStart, now, OPEN_CAP).filterKeys(isCounted)
        val eventDays = events.map { it.ts.atZone(zone).toLocalDate() }.toSet()

        val base = dates.map { d ->
            val s = start(d)
            val e = start(d.plusDays(1))
            val perAppIv = raw.mapValues { (_, l) ->
                l.mapNotNull { (a, b) ->
                    val ca = if (a.isBefore(s)) s else a
                    val cb = if (b.isAfter(e)) e else b
                    if (cb.isAfter(ca)) ca to cb else null
                }
            }.filterValues { it.isNotEmpty() }
            val perApp = perAppIv.mapValues { sum(it.value) }.filterValues { !it.isZero }
            DayUsage(d, d == today || d in eventDays, perApp, sum(perAppIv.values.flatten()))
        }
        val days = mergeDaily(base, daily, today, isCounted)

        val full = days.subList(days.size - 8, days.size - 1)
        val prev = days.subList(days.size - 15, days.size - 8)
        val last7 = days.takeLast(7)
        val prevComplete = prev.all { it.hasData }
        val total = { l: List<DayUsage> -> l.fold(Duration.ZERO) { a, d -> a + d.total } }
        val withData = full.filter { it.hasData }
        val avg = if (withData.isEmpty()) Duration.ZERO else total(withData).dividedBy(withData.size.toLong())

        val pkgs = days.flatMap { it.perApp.keys }.toSet()
        val apps = pkgs.associateWith { p ->
            val sum = { l: List<DayUsage> -> l.fold(Duration.ZERO) { a, d -> a + (d.perApp[p] ?: Duration.ZERO) } }
            AppHistory(p, last7.map { it.perApp[p] ?: Duration.ZERO }, if (prevComplete) trend(sum(full), sum(prev)) else null)
        }
        return History(days, avg, if (prevComplete) trend(total(full), total(prev)) else null, apps)
    }
}
