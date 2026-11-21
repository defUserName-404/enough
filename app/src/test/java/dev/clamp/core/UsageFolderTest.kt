package dev.clamp.core

import dev.clamp.core.UsageEvt.Type.CLOSE_ALL
import dev.clamp.core.UsageEvt.Type.OPEN_GATE
import dev.clamp.core.UsageEvt.Type.PAUSED
import dev.clamp.core.UsageEvt.Type.RESUMED
import dev.clamp.core.UsageEvt.Type.STOPPED
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageFolderTest {
    private fun ev(pkg: String, type: UsageEvt.Type, ts: java.time.Instant, cls: String = "A") = UsageEvt(pkg, type, ts, cls)
    private fun fold(e: List<UsageEvt>) = UsageFolder.fold(e, DAY_START, NOW)

    @Test fun single_interval() {
        val r = fold(listOf(ev("a", RESUMED, t(10, 0)), ev("a", PAUSED, t(10, 20))))
        assertEquals(Duration.ofMinutes(20), r["a"])
    }

    @Test fun opened_before_midnight() {
        val r = fold(listOf(ev("a", PAUSED, t(0, 15))))
        assertEquals(Duration.ofMinutes(15), r["a"])
    }

    @Test fun currently_resumed() {
        val r = fold(listOf(ev("a", RESUMED, t(14, 0))))
        assertEquals(Duration.ofMinutes(32), r["a"])
    }

    @Test fun duplicate_resumed_keeps_earliest() {
        val r = fold(listOf(ev("a", RESUMED, t(10, 0)), ev("a", RESUMED, t(10, 5)), ev("a", PAUSED, t(10, 20))))
        assertEquals(Duration.ofMinutes(20), r["a"])
    }

    @Test fun out_of_order_same_as_sorted() {
        val sorted = listOf(ev("a", RESUMED, t(10, 0)), ev("a", PAUSED, t(10, 20)), ev("a", RESUMED, t(11, 0)), ev("a", PAUSED, t(11, 10)))
        assertEquals(fold(sorted), fold(sorted.reversed()))
        assertEquals(Duration.ofMinutes(30), fold(sorted.reversed())["a"])
    }

    @Test fun activity_switch_within_app_merged() {
        val r = fold(listOf(
            ev("a", RESUMED, t(10, 0), "A"), ev("a", RESUMED, t(10, 10), "B"),
            ev("a", PAUSED, t(10, 10, 1), "A"), ev("a", PAUSED, t(10, 30), "B"),
        ))
        assertEquals(Duration.ofMinutes(30), r["a"])
    }

    @Test fun stopped_no_double_count_and_first_stopped_is_zero() {
        val r = fold(listOf(ev("a", RESUMED, t(10, 0)), ev("a", PAUSED, t(10, 20)), ev("a", STOPPED, t(10, 21))))
        assertEquals(Duration.ofMinutes(20), r["a"])
        val z = fold(listOf(ev("b", STOPPED, t(0, 10))))
        assertEquals(null, z["b"])
        val z2 = fold(listOf(ev("b", PAUSED, DAY_START.minusSeconds(600)), ev("b", STOPPED, t(0, 10))))
        assertEquals(null, z2["b"])
    }

    @Test fun lookback_clamped_to_day_start() {
        val r = fold(listOf(ev("a", RESUMED, DAY_START.minusSeconds(1800)), ev("a", PAUSED, t(0, 30))))
        assertEquals(Duration.ofMinutes(30), r["a"])
    }

    @Test fun events_after_now_ignored() {
        val r = fold(listOf(ev("a", RESUMED, t(10, 0)), ev("a", PAUSED, t(10, 5)), ev("a", RESUMED, t(15, 0)), ev("a", PAUSED, t(15, 30))))
        assertEquals(Duration.ofMinutes(5), r["a"])
    }

    @Test fun pkgs_independent_and_foreground() {
        val e = listOf(
            ev("a", RESUMED, t(10, 0)), ev("a", PAUSED, t(10, 10)),
            ev("b", RESUMED, t(11, 0)),
            ev("c", RESUMED, t(13, 0)),
        )
        val r = fold(e)
        assertEquals(Duration.ofMinutes(10), r["a"])
        assertEquals(Duration.ofMinutes(92), r["c"])
        assertEquals("c", UsageFolder.foreground(e, NOW))
        val all = e + listOf(ev("b", PAUSED, t(12, 0)), ev("c", PAUSED, t(13, 30)))
        assertNull(UsageFolder.foreground(all, NOW))
    }

    @Test fun screen_off_closes_all_open_sessions() {
        val e = listOf(ev("a", RESUMED, t(10, 0)), ev("b", RESUMED, t(10, 5), "B"), ev("", CLOSE_ALL, t(10, 30), ""))
        val r = fold(e)
        assertEquals(Duration.ofMinutes(30), r["a"])
        assertEquals(Duration.ofMinutes(25), r["b"])
        assertNull(UsageFolder.foreground(e, NOW))
    }

    @Test fun stale_open_from_yesterday_closed_by_screen_off() {
        val e = listOf(ev("a", RESUMED, DAY_START.minusSeconds(3600)), ev("", CLOSE_ALL, t(0, 20)))
        assertEquals(Duration.ofMinutes(20), fold(e)["a"])
        val before = listOf(ev("a", RESUMED, DAY_START.minusSeconds(7200)), ev("", CLOSE_ALL, DAY_START.minusSeconds(3600)))
        assertEquals(null, fold(before)["a"])
    }

    @Test fun stale_open_without_close_counts_from_day_start() {
        val e = listOf(ev("a", RESUMED, DAY_START.minusSeconds(20 * 3600)))
        assertEquals(Duration.ofMinutes(14 * 60 + 32), fold(e)["a"])
    }

    @Test fun resume_while_locked_does_not_count() {
        val e = listOf(ev("x", OPEN_GATE, t(8, 0)), ev("a", CLOSE_ALL, t(9, 0)), ev("a", RESUMED, t(9, 10)), ev("a", PAUSED, t(9, 20)))
        assertEquals(null, fold(e)["a"])
    }

    @Test fun resume_while_locked_then_unlock_counts_from_unlock() {
        val e = listOf(ev("a", CLOSE_ALL, t(9, 0)), ev("a", RESUMED, t(9, 10)), ev("a", OPEN_GATE, t(9, 20)), ev("a", PAUSED, t(9, 50)))
        assertEquals(Duration.ofMinutes(30), fold(e)["a"])
    }

    @Test fun close_all_then_resumed_without_unlock_does_not_count() {
        val e = listOf(ev("x", OPEN_GATE, t(8, 0)), ev("a", CLOSE_ALL, t(13, 0)), ev("a", RESUMED, t(13, 10)))
        assertEquals(null, fold(e)["a"])
        assertNull(UsageFolder.foreground(e, NOW))
    }

    @Test fun initial_gate_closed_when_first_gate_event_is_open() {
        val e = listOf(ev("a", RESUMED, t(8, 0)), ev("a", OPEN_GATE, t(9, 0)), ev("a", PAUSED, t(9, 10)))
        assertEquals(Duration.ofMinutes(10), fold(e)["a"])
    }

    // Device that never reports SCREEN_INTERACTIVE/KEYGUARD_HIDDEN: gating must switch off, or enforcement stops counting.
    @Test fun no_open_gate_events_means_no_gating() {
        val e = listOf(ev("a", CLOSE_ALL, t(9, 0)), ev("a", RESUMED, t(9, 10)), ev("a", PAUSED, t(9, 40)))
        assertEquals(Duration.ofMinutes(30), fold(e)["a"])
    }

    // Locked across midnight, unlocked before it: the session must start at dayStart, not before.
    @Test fun unlock_before_day_start_is_clamped() {
        val e = listOf(
            ev("x", CLOSE_ALL, DAY_START.minus(Duration.ofMinutes(30))),
            ev("a", RESUMED, DAY_START.minus(Duration.ofMinutes(20))),
            ev("x", OPEN_GATE, DAY_START.minus(Duration.ofMinutes(10))),
            ev("a", PAUSED, t(0, 15)),
        )
        assertEquals(Duration.ofMinutes(15), fold(e)["a"])
    }
}
