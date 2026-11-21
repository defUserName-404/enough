package dev.clamp.core

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TickerTest {
    class FakeCfg(var c: Config) : ConfigPort {
        var failGet = false
        override suspend fun get(): Config { if (failGet) error("corrupt"); return c }
        override suspend fun update(f: suspend (Config) -> Config): Config { c = f(c); return c }
    }
    class FakeUsage(var snap: UsageSnapshot?) : UsagePort {
        var throwOnRead = false
        override fun read(dayStart: Instant, now: Instant): UsageSnapshot? {
            if (throwOnRead) throw IllegalStateException("boom")
            return snap
        }
    }
    class FakeDevice : DevicePort {
        var owner = true
        var refuseSuspend = emptySet<String>()
        var refuseUnsuspend = emptySet<String>()
        var throwOn: Boolean? = null // suspended flag for which calls throw
        var missing = emptyList<String>()
        var releaseOk = true
        var protectionCalls = 0
        var releasedPkgs: Collection<String> = emptyList()
        val calls = mutableListOf<Pair<Set<String>, Boolean>>()
        override fun isOwner() = owner
        override fun setSuspended(pkgs: Collection<String>, suspended: Boolean): SuspendResult {
            calls += pkgs.toSet() to suspended
            if (throwOn == suspended) return SuspendResult(emptyList(), threw = true)
            val refuse = if (suspended) refuseSuspend else refuseUnsuspend
            return SuspendResult(pkgs.filter { it in refuse })
        }
        override fun applyProtection(): List<String> { protectionCalls++; return missing }
        override fun release(pkgs: Collection<String>): Boolean { releasedPkgs = pkgs; return releaseOk }
    }
    class FakeNotify : NotifyPort {
        val fail = mutableListOf<Boolean>()
        val warns = mutableListOf<String>()
        var lost = 0
        var cfgErr = 0
        override fun warn(pkg: String, left: Duration) { warns += pkg }
        override fun failClosed(show: Boolean) { fail += show }
        override fun lostOwner() { lost++ }
        override fun configError() { cfgErr++ }
    }

    private var clock: Clock = CLOCK
    private val base = Config(phase = Phase.ACTIVE, targets = listOf(Target("a", 20), Target("b", 30)))
    private val cfg = FakeCfg(base)
    private val usage = FakeUsage(UsageSnapshot(emptyMap(), null))
    private val device = FakeDevice()
    private val notify = FakeNotify()
    private val ticker = Ticker(cfg, usage, device, notify, Releaser(cfg, device)) { clock }
    private val over = UsageSnapshot(mapOf("a" to Duration.ofMinutes(25)), null)

    @Test fun due_remove_unsuspends_and_removes() = runTest {
        cfg.c = base.copy(pending = listOf(PendingChange("p", Change.Remove("a"), NOW.minusSeconds(1))))
        ticker.tick()
        assertTrue(device.calls.contains(setOf("a") to false))
        assertEquals(null, cfg.c.target("a"))
        assertTrue(cfg.c.pending.isEmpty())
        assertTrue(cfg.c.stuckSuspended.isEmpty())
    }

    @Test fun usage_null_fail_closed_once() = runTest {
        usage.snap = null
        ticker.tick(); ticker.tick()
        assertEquals(listOf(true), notify.fail)
        assertTrue(device.calls.any { it == setOf("a", "b") to true })
        usage.snap = UsageSnapshot(emptyMap(), null)
        ticker.tick()
        assertEquals(listOf(true, false), notify.fail)
    }

    @Test fun usage_read_exception_fails_closed() = runTest {
        usage.throwOnRead = true
        ticker.tick()
        assertTrue(device.calls.contains(setOf("a", "b") to true))
        assertEquals(listOf(true), notify.fail)
    }

    @Test fun config_read_exception_notifies() = runTest {
        cfg.failGet = true
        assertEquals(TickResult.Next(Ticker.SLOW), ticker.tick())
        assertEquals(1, notify.cfgErr)
        assertTrue(device.calls.isEmpty())
    }

    @Test fun delay_by_foreground() = runTest {
        usage.snap = UsageSnapshot(emptyMap(), "a")
        assertEquals(TickResult.Next(Ticker.FAST), ticker.tick())
        usage.snap = UsageSnapshot(emptyMap(), "other")
        assertEquals(TickResult.Next(Ticker.SLOW), ticker.tick())
    }

    @Test fun diffs_then_heartbeat_full() = runTest {
        usage.snap = over
        ticker.tick()
        assertEquals(setOf("a") to true, device.calls.first { it.second })
        device.calls.clear()
        clock = clockAt(NOW.plusSeconds(30))
        ticker.tick()
        assertTrue(device.calls.isEmpty())
        clock = clockAt(NOW.plus(Duration.ofMinutes(6)))
        ticker.tick()
        assertTrue(device.calls.contains(setOf("a") to true))
        assertTrue(device.calls.contains(setOf("b") to false))
    }

    @Test fun refused_suspend_goes_to_unsuspendable_and_clears_on_success() = runTest {
        usage.snap = over
        device.refuseSuspend = setOf("a")
        ticker.tick()
        assertEquals(setOf("a"), cfg.c.unsuspendable)
        device.refuseSuspend = emptySet()
        clock = clockAt(NOW.plus(Duration.ofMinutes(6))) // heartbeat: full re-apply retries
        ticker.tick()
        assertTrue(cfg.c.unsuspendable.isEmpty())
    }

    @Test fun suspend_exception_is_transient_nothing_persisted_then_retried() = runTest {
        usage.snap = over
        device.throwOn = true
        ticker.tick()
        assertTrue(cfg.c.unsuspendable.isEmpty())
        device.throwOn = null
        device.calls.clear()
        clock = clockAt(NOW.plusSeconds(15))
        ticker.tick()
        assertTrue(device.calls.contains(setOf("a") to true))
    }

    @Test fun unsuspend_failure_persists_stuck_retries_and_clears() = runTest {
        cfg.c = base.copy(pending = listOf(PendingChange("p", Change.Remove("a"), NOW.minusSeconds(1))))
        device.refuseUnsuspend = setOf("a")
        ticker.tick()
        assertEquals(setOf("a"), cfg.c.stuckSuspended)
        // retried every tick while failing
        device.calls.clear()
        clock = clockAt(NOW.plusSeconds(15))
        ticker.tick()
        assertTrue(device.calls.contains(setOf("a") to false))
        assertEquals(setOf("a"), cfg.c.stuckSuspended)
        // success clears it
        device.refuseUnsuspend = emptySet()
        clock = clockAt(NOW.plusSeconds(30))
        ticker.tick()
        assertTrue(cfg.c.stuckSuspended.isEmpty())
    }

    @Test fun normal_unsuspend_exception_becomes_stuck() = runTest {
        usage.snap = over
        ticker.tick() // a suspended
        device.throwOn = false
        usage.snap = UsageSnapshot(emptyMap(), null)
        clock = clockAt(NOW.plusSeconds(15))
        ticker.tick()
        assertEquals(setOf("a"), cfg.c.stuckSuspended)
        device.throwOn = null
        clock = clockAt(NOW.plusSeconds(30))
        ticker.tick()
        assertTrue(cfg.c.stuckSuspended.isEmpty())
    }

    @Test fun protection_missing_persisted_and_reasserted_on_heartbeat() = runTest {
        device.missing = listOf("SAFE BOOT")
        ticker.tick()
        assertEquals(listOf("SAFE BOOT"), cfg.c.protectionMissing)
        assertEquals(1, device.protectionCalls)
        clock = clockAt(NOW.plusSeconds(30))
        ticker.tick()
        assertEquals(1, device.protectionCalls)
        device.missing = emptyList()
        clock = clockAt(NOW.plus(Duration.ofMinutes(6)))
        ticker.tick()
        assertEquals(2, device.protectionCalls)
        assertTrue(cfg.c.protectionMissing.isEmpty())
    }

    @Test fun release_success_resets_config() = runTest {
        cfg.c = base.copy(
            unsuspendable = setOf("u"), stuckSuspended = setOf("s"),
            pending = listOf(PendingChange("r", Change.Release, NOW.minusSeconds(1))),
        )
        assertEquals(TickResult.Release, ticker.tick())
        assertEquals(Config(), cfg.c)
        assertEquals(setOf("a", "b", "u", "s"), device.releasedPkgs.toSet())
    }

    @Test fun release_partial_failure_stays_releasing_then_retries() = runTest {
        cfg.c = base.copy(pending = listOf(PendingChange("r", Change.Release, NOW.minusSeconds(1))))
        device.releaseOk = false
        assertEquals(TickResult.Next(Ticker.SLOW), ticker.tick())
        assertEquals(Phase.RELEASING, cfg.c.phase)
        assertTrue(cfg.c.releaseFailed)
        assertTrue(cfg.c.pending.isEmpty())
        assertEquals(2, cfg.c.targets.size)
        // not owner doesn't matter while RELEASING
        device.releaseOk = true
        assertEquals(TickResult.Release, ticker.tick())
        assertEquals(Phase.SETUP, cfg.c.phase)
        assertFalse(cfg.c.releaseFailed)
    }

    @Test fun release_with_other_due_changes_same_tick() = runTest {
        cfg.c = base.copy(
            pending = listOf(
                PendingChange("1", Change.Remove("a"), NOW.minusSeconds(30)),
                PendingChange("2", Change.RaiseLimit(Target("b", 60)), NOW.minusSeconds(20)),
                PendingChange("3", Change.Release, NOW.minusSeconds(10)),
            ),
        )
        device.refuseUnsuspend = setOf("a")
        device.releaseOk = false
        ticker.tick()
        assertEquals(Phase.RELEASING, cfg.c.phase)
        assertTrue(cfg.c.pending.isEmpty())
        assertTrue(device.calls.contains(setOf("a") to false))
        assertEquals(setOf("a"), cfg.c.stuckSuspended)
        assertTrue("a" in device.releasedPkgs) // stuck removed target still released
        assertEquals(60, cfg.c.target("b")?.limitMin)
    }

    @Test fun not_owner() = runTest {
        device.owner = false
        assertEquals(TickResult.NotOwner, ticker.tick())
        assertEquals(1, notify.lost)
    }
}
