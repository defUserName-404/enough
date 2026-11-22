package app.enough.usage.domain

import java.time.Duration
import java.time.Instant

object UsageFolder {
    private class Scan(
        val intervals: Map<String, MutableList<Pair<Instant, Instant>>>,
        val open: Map<Pair<String, String>, Instant>,
    )

    /**
     * [openCap] (history only, never enforcement): caps a session that has no closing event, and the
     * assumed start of a PAUSED with no RESUMED.
     */
    private fun scan(events: List<UsageEvt>, dayStart: Instant, now: Instant, openCap: Duration? = null): Scan {
        val sorted = events.filter { !it.ts.isAfter(now) }
            .sortedWith(compareBy<UsageEvt> { it.ts }.thenBy { if (it.type != UsageEvt.Type.RESUMED) 0 else 1 })
        val open = LinkedHashMap<Pair<String, String>, Instant>()
        val seen = HashSet<Pair<String, String>>()
        val intervals = HashMap<String, MutableList<Pair<Instant, Instant>>>()
        // Screen gate: open unless the first gate event is an OPEN_GATE. RESUMED while closed is remembered, not counted.
        // Fail-safe for enforcement: a device that never reports SCREEN_INTERACTIVE/KEYGUARD_HIDDEN must not
        // end up with a gate that closes forever (that would stop counting usage), so gating is off without OPEN_GATE.
        val gating = sorted.any { it.type == UsageEvt.Type.OPEN_GATE }
        var gateOpen = !gating || sorted.firstOrNull { it.type == UsageEvt.Type.OPEN_GATE || it.type == UsageEvt.Type.CLOSE_ALL }
            ?.type != UsageEvt.Type.OPEN_GATE
        val pending = LinkedHashMap<Pair<String, String>, Instant>()
        fun add(pkg: String, a: Instant, b: Instant) {
            intervals.getOrPut(pkg) { mutableListOf() }.add(a to b)
        }
        for (e in sorted) {
            val key = e.pkg to e.cls
            val t = if (e.ts.isBefore(dayStart)) dayStart else e.ts
            when (e.type) {
                UsageEvt.Type.RESUMED -> {
                    // Duplicate RESUMED keeps the earliest. Foreground uses the raw ts, so keep the raw one.
                    if (!gateOpen) {
                        if (key !in open && key !in pending) pending[key] = t
                    } else if (key !in open) open[key] = if (dayStart == Instant.MIN) e.ts else t
                    seen.add(key)
                }
                UsageEvt.Type.PAUSED -> {
                    pending.remove(key)
                    val start = open.remove(key)
                    if (start != null) add(e.pkg, start, t)
                    else if (key !in seen && gateOpen) {
                        val from = if (openCap != null && Duration.between(dayStart, t) > openCap) t.minus(openCap) else dayStart
                        add(e.pkg, from, t)
                    }
                    seen.add(key)
                }
                UsageEvt.Type.STOPPED -> {
                    pending.remove(key)
                    val start = open.remove(key)
                    if (start != null) add(e.pkg, start, t)
                    seen.add(key)
                }
                UsageEvt.Type.CLOSE_ALL -> {
                    for ((k, start) in open) add(k.first, start, t)
                    open.clear()
                    gateOpen = !gating
                }
                UsageEvt.Type.OPEN_GATE -> {
                    gateOpen = true
                    for ((k, _) in pending) if (k !in open) open[k] = if (dayStart == Instant.MIN) e.ts else t
                    pending.clear()
                }
            }
        }
        for ((key, start) in open) {
            val end = if (openCap != null && Duration.between(start, now) > openCap) start.plus(openCap) else now
            add(key.first, start, end)
        }
        return Scan(intervals, open)
    }

    /** Raw per-package intervals (not merged), clipped to [windowStart, now]. */
    fun intervals(
        events: List<UsageEvt>,
        windowStart: Instant,
        now: Instant,
        openCap: Duration? = null,
    ): Map<String, List<Pair<Instant, Instant>>> = scan(events, windowStart, now, openCap).intervals

    fun fold(events: List<UsageEvt>, dayStart: Instant, now: Instant): Map<String, Duration> {
        val s = scan(events, dayStart, now)
        val out = HashMap<String, Duration>()
        for ((pkg, list) in s.intervals) {
            val sorted = list.filter { !it.second.isBefore(it.first) }.sortedBy { it.first }
            var total = Duration.ZERO
            var curS: Instant? = null
            var curE: Instant? = null
            for ((a, b) in sorted) {
                if (curS == null) { curS = a; curE = b }
                else if (!a.isAfter(curE)) { if (b.isAfter(curE)) curE = b }
                else { total += Duration.between(curS, curE); curS = a; curE = b }
            }
            if (curS != null) total += Duration.between(curS, curE)
            if (!total.isZero) out[pkg] = total
        }
        return out
    }

    fun foreground(events: List<UsageEvt>, now: Instant): String? {
        val s = scan(events, Instant.MIN, now)
        return s.open.entries.maxByOrNull { it.value }?.key?.first
    }
}
