package dev.clamp.core

import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcerTest {
    private val enf = Enforcer(CLOCK)
    private val cfg = Config(phase = Phase.ACTIVE, targets = listOf(Target("a", 20), Target("b", 30)))
    private fun min(m: Long) = Duration.ofMinutes(m)

    @Test fun suspend_and_unsuspend() {
        val d = enf.decide(cfg, mapOf("a" to min(25), "b" to min(5)))
        assertEquals(setOf("a"), d.suspend)
        assertEquals(setOf("b"), d.unsuspend)
        assertEquals(false, d.failClosed)
    }

    @Test fun null_usage_fails_closed() {
        val d = enf.decide(cfg, null)
        assertEquals(setOf("a", "b"), d.suspend)
        assertTrue(d.failClosed)
        assertTrue(d.warn.isEmpty())
    }

    @Test fun warn_once_per_day() {
        val u = mapOf("a" to min(18) + Duration.ofSeconds(30))
        assertEquals(setOf("a"), enf.decide(cfg, u).warn)
        val today = LocalDate.of(2026, 11, 21)
        assertTrue(enf.decide(cfg.copy(warned = mapOf("a" to today)), u).warn.isEmpty())
        assertEquals(setOf("a"), enf.decide(cfg.copy(warned = mapOf("a" to today.minusDays(1))), u).warn)
    }

    @Test fun exactly_at_limit_suspends() {
        val d = enf.decide(cfg, mapOf("a" to min(20)))
        assertTrue("a" in d.suspend)
        assertTrue("a" !in d.warn)
    }

    @Test fun midnight_rollover() {
        val next = Enforcer(clockAt(NOW.plus(Duration.ofDays(1))))
        val d = next.decide(cfg.copy(warned = mapOf("a" to LocalDate.of(2026, 11, 21))), mapOf("a" to min(1)))
        assertTrue(d.suspend.isEmpty())
        assertEquals(setOf("a", "b"), d.unsuspend)
    }
}
