package app.enough.core

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangePolicyTest {
    private var n = 0
    private val policy = ChangePolicy(CLOCK) { "id${n++}" }
    private val active = Config(phase = Phase.ACTIVE, targets = listOf(Target("a", 20), Target("b", 30)))

    @Test fun add_new_app_is_tighten() {
        val o = policy.submit(active, Change.AddOrLower(Target("c", 10)))
        assertEquals(Kind.TIGHTEN, o.kind)
        assertEquals(10, o.config.target("c")?.limitMin)
        assertTrue(o.config.pending.isEmpty())
    }

    @Test fun lowering_is_tighten() {
        val o = policy.submit(active, policy.limitChange(active, "a", 10)!!)
        assertEquals(Kind.TIGHTEN, o.kind)
        assertEquals(10, o.config.target("a")?.limitMin)
    }

    @Test fun raising_is_pending() {
        val o = policy.submit(active, policy.limitChange(active, "a", 40)!!)
        assertEquals(Kind.LOOSEN, o.kind)
        assertEquals(20, o.config.target("a")?.limitMin)
        assertEquals(NOW + Duration.ofHours(24), o.config.pending.single().applyAt)
    }

    @Test fun remove_waits_then_applies() {
        val o = policy.submit(active.copy(warned = mapOf("a" to DAY_START.atZone(java.time.ZoneOffset.UTC).toLocalDate())), Change.Remove("a"))
        assertEquals(Kind.LOOSEN, o.kind)
        assertEquals(o.config, policy.applyDue(o.config).config)
        val later = ChangePolicy(clockAt(NOW.plus(Duration.ofHours(24))))
        val d = later.applyDue(o.config)
        assertEquals(null, d.config.target("a"))
        assertTrue("a" !in d.config.warned)
        assertEquals(listOf<Change>(Change.Remove("a")), d.applied)
        assertTrue(d.config.pending.isEmpty())
    }

    @Test fun release_loosens() {
        val o = policy.submit(active, Change.Release)
        assertEquals(Kind.LOOSEN, o.kind)
        val d = ChangePolicy(clockAt(NOW.plus(Duration.ofHours(25)))).applyDue(o.config)
        assertEquals(listOf<Change>(Change.Release), d.applied)
        assertEquals(active.targets, d.config.targets)
    }

    @Test fun same_pkg_latest_wins_fresh_timer() {
        val o1 = policy.submit(active, Change.RaiseLimit(Target("a", 40)))
        val p2 = ChangePolicy(clockAt(NOW.plusSeconds(3600))) { "z" }
        val o2 = p2.submit(o1.config, Change.RaiseLimit(Target("a", 60)))
        val p = o2.config.pending.single()
        assertEquals(Change.RaiseLimit(Target("a", 60)), p.change)
        assertEquals(NOW.plusSeconds(3600) + Duration.ofHours(24), p.applyAt)
    }

    @Test fun tighten_clears_pending_for_pkg() {
        val o1 = policy.submit(active, Change.RaiseLimit(Target("a", 40)))
        val o2 = policy.submit(o1.config, Change.AddOrLower(Target("a", 10)))
        assertEquals(10, o2.config.target("a")?.limitMin)
        assertTrue(o2.config.pending.isEmpty())
    }

    @Test fun cancel_removes_only_that() {
        val c1 = policy.submit(active, Change.RaiseLimit(Target("a", 40))).config
        val c2 = policy.submit(c1, Change.RaiseLimit(Target("b", 60))).config
        val id = c2.pending.first().id
        val c3 = policy.cancel(c2, id)
        assertEquals(1, c3.pending.size)
        assertTrue(c3.pending.none { it.id == id })
    }

    @Test fun setup_phase_applies_immediately() {
        val setup = active.copy(phase = Phase.SETUP)
        val r = policy.submit(setup, Change.RaiseLimit(Target("a", 90)))
        assertEquals(90, r.config.target("a")?.limitMin)
        val rm = policy.submit(setup, Change.Remove("a"))
        assertEquals(null, rm.config.target("a"))
        assertTrue(rm.config.pending.isEmpty())
    }

    @Test fun limit_change_clamps_and_noop() {
        assertEquals(Change.AddOrLower(Target("x", 5)), policy.limitChange(active, "x", 1))
        assertEquals(Change.AddOrLower(Target("x", 240)), policy.limitChange(active, "x", 999))
        assertEquals(Change.AddOrLower(Target("x", 20)), policy.limitChange(active, "x", 22))
        assertEquals(null, policy.limitChange(active, "a", 20))
        val o = policy.submit(active, Change.AddOrLower(Target("a", 20)))
        assertEquals(Kind.NOOP, o.kind)
        assertEquals(active, o.config)
    }

    @Test fun tighten_cancels_pending_remove() {
        val c1 = policy.submit(active, Change.Remove("a")).config
        assertEquals(1, c1.pending.size)
        val c2 = policy.submit(c1, Change.AddOrLower(Target("a", 10))).config
        assertTrue(c2.pending.isEmpty())
        assertEquals(10, c2.target("a")?.limitMin)
    }

    @Test fun add_mode_higher_limit_on_existing_pkg_is_loosen() {
        val o = policy.submit(active, Change.AddOrLower(Target("a", 40)))
        assertEquals(Kind.LOOSEN, o.kind)
        assertEquals(20, o.config.target("a")?.limitMin)
        assertEquals(1, o.config.pending.size)
    }

    @Test fun limit_change_raise_on_existing_target_is_raise_and_pending_24h() {
        val ch = policy.limitChange(active, "a", 40)
        assertEquals(Change.RaiseLimit(Target("a", 40)), ch)
        val o = policy.submit(active, ch!!)
        assertEquals(NOW + Duration.ofHours(24), o.config.pending.single().applyAt)
    }
}
