package app.enough.core

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class Decision(
    val suspend: Set<String>,
    val unsuspend: Set<String>,
    val warn: Set<String>,
    val failClosed: Boolean,
)

class Enforcer(private val clock: Clock) {
    companion object {
        val WARN_AT: Duration = Duration.ofMinutes(2)
    }

    fun today(): LocalDate = LocalDate.now(clock)
    fun dayStart(): Instant = today().atStartOfDay(clock.zone).toInstant()

    fun decide(config: Config, usage: Map<String, Duration>?): Decision {
        val all = config.targets.map { it.pkg }.toSet()
        if (usage == null) return Decision(all, emptySet(), emptySet(), true)
        val today = today()
        val suspend = HashSet<String>()
        val warn = HashSet<String>()
        for (t in config.targets) {
            val limit = Duration.ofMinutes(t.limitMin.toLong())
            val used = usage[t.pkg] ?: Duration.ZERO
            if (used >= limit) suspend.add(t.pkg)
            else if (limit - used <= WARN_AT && config.warned[t.pkg] != today) warn.add(t.pkg)
        }
        return Decision(suspend, all - suspend, warn, false)
    }
}
