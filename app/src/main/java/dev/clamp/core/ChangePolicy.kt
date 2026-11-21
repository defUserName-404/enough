package dev.clamp.core

import java.time.Clock
import java.time.Duration
import java.util.UUID

enum class Kind { TIGHTEN, LOOSEN, NOOP }

data class Outcome(val config: Config, val kind: Kind, val applied: List<Change>)

class ChangePolicy(
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    companion object {
        val DELAY: Duration = Duration.ofHours(24)

        fun keyOf(c: Change): String = when (c) {
            is Change.AddOrLower -> "pkg:${c.target.pkg}"
            is Change.RaiseLimit -> "pkg:${c.target.pkg}"
            is Change.Remove -> "pkg:${c.pkg}"
            Change.Release -> "release"
        }
    }

    fun limitChange(config: Config, pkg: String, newLimitMin: Int): Change? {
        val m = Limits.clamp(newLimitMin)
        val existing = config.target(pkg)
        return when {
            existing == null -> Change.AddOrLower(Target(pkg, m))
            m == existing.limitMin -> null
            m < existing.limitMin -> Change.AddOrLower(Target(pkg, m))
            else -> Change.RaiseLimit(Target(pkg, m))
        }
    }

    fun classify(change: Change, config: Config): Kind = when (change) {
        is Change.AddOrLower -> classifyLimit(change.target, config)
        is Change.RaiseLimit -> classifyLimit(change.target, config)
        is Change.Remove -> if (config.target(change.pkg) != null) Kind.LOOSEN else Kind.NOOP
        Change.Release -> Kind.LOOSEN
    }

    private fun classifyLimit(t: Target, config: Config): Kind {
        val existing = config.target(t.pkg) ?: return Kind.TIGHTEN
        return when {
            t.limitMin < existing.limitMin -> Kind.TIGHTEN
            t.limitMin == existing.limitMin -> Kind.NOOP
            else -> Kind.LOOSEN
        }
    }

    fun submit(config: Config, change: Change): Outcome {
        if (config.phase != Phase.ACTIVE) {
            return Outcome(apply(config, change), Kind.TIGHTEN, listOf(change))
        }
        val key = keyOf(change)
        return when (val kind = classify(change, config)) {
            Kind.NOOP -> Outcome(config, Kind.NOOP, emptyList())
            Kind.TIGHTEN -> {
                val cfg = apply(config, change)
                Outcome(cfg.copy(pending = cfg.pending.filter { keyOf(it.change) != key }), kind, listOf(change))
            }
            Kind.LOOSEN -> {
                val p = PendingChange(newId(), change, clock.instant().plus(DELAY))
                Outcome(config.copy(pending = config.pending.filter { keyOf(it.change) != key } + p), kind, emptyList())
            }
        }
    }

    fun applyDue(config: Config): Outcome {
        val now = clock.instant()
        val due = config.pending.filter { !it.applyAt.isAfter(now) }
        return applyPending(config, due)
    }

    private fun applyPending(config: Config, sel: List<PendingChange>): Outcome {
        if (sel.isEmpty()) return Outcome(config, Kind.NOOP, emptyList())
        val ordered = sel.sortedBy { it.applyAt }
        var cfg = config
        for (p in ordered) cfg = apply(cfg, p.change)
        val ids = ordered.map { it.id }.toSet()
        cfg = cfg.copy(pending = cfg.pending.filter { it.id !in ids })
        return Outcome(cfg, Kind.LOOSEN, ordered.map { it.change })
    }

    fun cancel(config: Config, id: String): Config =
        config.copy(pending = config.pending.filter { it.id != id })

    private fun apply(cfg: Config, change: Change): Config = when (change) {
        is Change.AddOrLower -> upsert(cfg, change.target)
        is Change.RaiseLimit -> upsert(cfg, change.target)
        is Change.Remove -> cfg.copy(
            targets = cfg.targets.filter { it.pkg != change.pkg },
            warned = cfg.warned - change.pkg,
        )
        Change.Release -> cfg
    }

    private fun upsert(cfg: Config, t: Target): Config =
        if (cfg.target(t.pkg) != null) cfg.copy(targets = cfg.targets.map { if (it.pkg == t.pkg) t else it })
        else cfg.copy(targets = cfg.targets + t)
}
