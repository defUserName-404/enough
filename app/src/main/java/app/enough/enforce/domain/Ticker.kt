package app.enough.enforce.domain

import app.enough.limits.domain.Change
import app.enough.limits.domain.ChangePolicy
import app.enough.limits.domain.ConfigPort
import app.enough.limits.domain.Phase
import app.enough.usage.domain.UsagePort
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

sealed interface TickResult {
    data class Next(val delay: Duration) : TickResult
    /** Release fully succeeded; stop the service. */
    data object Release : TickResult
    data object Stop : TickResult
    data object NotOwner : TickResult
}

class Ticker(
    private val cfg: ConfigPort,
    private val usage: UsagePort,
    private val device: DevicePort,
    private val notify: NotifyPort,
    private val releaser: Releaser,
    private val clock: () -> Clock,
) {
    companion object {
        val FAST: Duration = Duration.ofSeconds(15)
        val SLOW: Duration = Duration.ofSeconds(60)
        val HEARTBEAT: Duration = Duration.ofMinutes(5)
    }

    private var applied: Set<String>? = null
    private var lastTickAt: Instant? = null
    private var lastDay: LocalDate? = null

    private suspend fun releaseOrRetry(): TickResult =
        if (releaser.attempt()) TickResult.Release else TickResult.Next(SLOW)

    suspend fun tick(): TickResult {
        val c = clock()
        val now = c.instant()
        val policy = ChangePolicy(c)
        val enf = Enforcer(c)

        var config = try {
            cfg.get()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notify.configError() // existing OS suspensions stay; retry next tick
            return TickResult.Next(SLOW)
        }
        when (config.phase) {
            Phase.SETUP -> return TickResult.Stop
            Phase.RELEASING -> return releaseOrRetry()
            Phase.ACTIVE -> Unit
        }
        if (!device.isOwner()) {
            notify.lostOwner()
            return TickResult.NotOwner
        }

        if (config.pending.any { !it.applyAt.isAfter(now) }) {
            var appliedChanges: List<Change> = emptyList()
            cfg.update {
                val o = policy.applyDue(it)
                appliedChanges = o.applied
                // Release flips the phase in the same write that removes the pending entry.
                if (o.applied.any { ch -> ch is Change.Release }) o.config.copy(phase = Phase.RELEASING) else o.config
            }
            val removed = appliedChanges.filterIsInstance<Change.Remove>().map { it.pkg }
            if (removed.isNotEmpty()) {
                val r = device.setSuspended(removed, false)
                val failed = if (r.threw) removed else r.refused
                if (failed.isNotEmpty()) cfg.update { it.copy(stuckSuspended = it.stuckSuspended + failed) }
            }
            if (appliedChanges.any { it is Change.Release }) return releaseOrRetry()
            config = cfg.get()
        }

        val snap = try {
            usage.read(enf.dayStart(), now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // fail closed
        }
        val d = enf.decide(config, snap?.usage)
        val today = enf.today()

        val prev = applied
        val last = lastTickAt
        val full = prev == null || last == null || Duration.between(last, now) > HEARTBEAT || today != lastDay
        val protection = if (full) device.applyProtection() else null

        val toSuspend = if (full) d.suspend else d.suspend - prev!! - config.unsuspendable
        val stuckWanted = config.stuckSuspended - d.suspend
        val toUnsuspend = ((if (full) d.unsuspend else prev!! - d.suspend) + stuckWanted) - config.unsuspendable

        val rs = if (toSuspend.isNotEmpty()) device.setSuspended(toSuspend, true) else SuspendResult()
        val failedS: Set<String> = if (rs.threw) toSuspend else rs.refused.toSet()
        val ru = if (toUnsuspend.isNotEmpty()) device.setSuspended(toUnsuspend, false) else SuspendResult()
        val failedU: Set<String> = if (ru.threw) toUnsuspend else ru.refused.toSet()
        applied = d.suspend - failedS

        val addUnsusp: Set<String> = if (rs.threw) emptySet() else rs.refused.toSet()
        val clearUnsusp: Set<String> = toSuspend - failedS
        val clearStuck: Set<String> = (toUnsuspend - failedU) + (config.stuckSuspended intersect d.suspend)

        val notifyFail = d.failClosed && !config.failClosedNotified
        val clearFail = !d.failClosed && config.failClosedNotified
        if (notifyFail) notify.failClosed(true)
        if (clearFail) notify.failClosed(false)

        val warnedNew = HashMap<String, LocalDate>()
        for (pkg in d.warn) {
            val limit = Duration.ofMinutes((config.target(pkg)?.limitMin ?: continue).toLong())
            val used = snap?.usage?.get(pkg) ?: Duration.ZERO
            notify.warn(pkg, limit - used)
            warnedNew[pkg] = today
        }
        val stale = config.warned.any { it.value != today }

        val newUnsusp = (config.unsuspendable + addUnsusp) - clearUnsusp
        val newStuck = (config.stuckSuspended - clearStuck) + failedU
        val protChanged = protection != null && protection != config.protectionMissing
        if (newUnsusp != config.unsuspendable || newStuck != config.stuckSuspended || notifyFail || clearFail ||
            warnedNew.isNotEmpty() || stale || protChanged
        ) {
            cfg.update { cur ->
                var n = cur.copy(
                    unsuspendable = (cur.unsuspendable + addUnsusp) - clearUnsusp,
                    stuckSuspended = (cur.stuckSuspended - clearStuck) + failedU,
                )
                if (protection != null) n = n.copy(protectionMissing = protection)
                if (notifyFail) n = n.copy(failClosedNotified = true)
                if (clearFail) n = n.copy(failClosedNotified = false)
                n.copy(warned = (n.warned + warnedNew).filterValues { it == today })
            }
        }

        lastTickAt = now
        lastDay = today
        val fg = snap?.foreground
        return TickResult.Next(if (fg != null && config.target(fg) != null) FAST else SLOW)
    }
}
