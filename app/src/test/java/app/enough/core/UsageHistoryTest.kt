package app.enough.core

import app.enough.core.UsageEvt.Type.CLOSE_ALL
import app.enough.core.UsageEvt.Type.PAUSED
import app.enough.core.UsageEvt.Type.RESUMED
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageHistoryTest {
    private val today = LocalDate.of(2026, 11, 21)
    private fun at(daysAgo: Int, h: Int, m: Int = 0): Instant =
        today.minusDays(daysAgo.toLong()).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(h * 3600L + m * 60L)

    private fun session(pkg: String, daysAgo: Int, h1: Int, m1: Int, h2: Int, m2: Int, cls: String = "A") = listOf(
        UsageEvt(pkg, RESUMED, at(daysAgo, h1, m1), cls), UsageEvt(pkg, PAUSED, at(daysAgo, h2, m2), cls),
    )

    private fun build(
        e: List<UsageEvt>,
        daily: Map<LocalDate, Map<String, Duration>> = emptyMap(),
        counted: (String) -> Boolean = { true },
    ) = UsageHistory.build(e, today, NOW, ZoneOffset.UTC, counted, daily)

    /** One hour per day on [daysAgo] range. */
    private fun hours(range: IntRange, hrs: Int) = range.flatMap { session("a", it, 10, 0, 10 + hrs, 0) }

    @Test fun fifteen_days_and_midnight_split() {
        val h = build(listOf(UsageEvt("a", RESUMED, at(1, 23, 30), "A"), UsageEvt("a", PAUSED, at(0, 0, 30), "A")))
        assertEquals(15, h.days.size)
        assertEquals(Duration.ofMinutes(30), h.days[13].perApp["a"])
        assertEquals(Duration.ofMinutes(30), h.today.perApp["a"])
    }

    @Test fun union_not_double_counted_but_per_app_kept() {
        val h = build(session("a", 0, 10, 0, 11, 0) + session("b", 0, 10, 30, 11, 30))
        assertEquals(Duration.ofMinutes(90), h.today.total)
        assertEquals(Duration.ofMinutes(60), h.today.perApp["a"])
    }

    @Test fun excluded_packages_not_counted() {
        val e = session("a", 0, 10, 0, 10, 20) + session("launcher", 0, 11, 0, 12, 0)
        val h = build(e, counted = { it == "a" })
        assertEquals(Duration.ofMinutes(20), h.today.total)
        assertEquals(setOf("a"), h.today.perApp.keys)
    }

    @Test fun avg_excludes_today_and_ignores_no_data_days() {
        // full7 = daysAgo 1..7; only 1..4 have data (2h each); today has 5h which must not count
        val e = hours(1..4, 2) + session("a", 0, 5, 0, 10, 0)
        val h = build(e)
        assertEquals(Duration.ofHours(2), h.avg7)
        assertFalse(h.days[14 - 5].hasData)
    }

    @Test fun trend_uses_full_days_only_up_and_down() {
        val up = hours(8..14, 1) + hours(1..7, 2) + session("a", 0, 5, 0, 12, 0)
        assertEquals(Trend(100, true), build(up).trend)
        assertEquals(Trend(100, true), build(up).apps.getValue("a").trend)
        val down = hours(8..14, 2) + hours(1..7, 1)
        assertEquals(Trend(50, false), build(down).trend)
    }

    @Test fun trend_edge_cases() {
        // a previous day lacks data -> null
        assertNull(build(hours(8..13, 1) + hours(1..7, 2)).trend)
        // previous total zero (only screen events) -> null
        val screenOnly = (8..14).map { UsageEvt("", CLOSE_ALL, at(it, 3), "") } + hours(1..7, 2)
        assertNull(build(screenOnly).trend)
        // under 1% change -> null
        val tiny = hours(8..14, 10) + hours(1..7, 10) + session("a", 1, 10, 0, 20, 1)
        assertNull(build(tiny).trend)
    }

    @Test fun missing_days_fall_back_to_daily_stats_as_approx() {
        val daily = (8..14).associate { today.minusDays(it.toLong()) to mapOf("a" to Duration.ofHours(1), "dialer" to Duration.ofHours(3)) }
        val h = build(hours(1..7, 2), daily, counted = { it == "a" })
        assertTrue(h.days[0].approx)
        assertEquals(Duration.ofHours(1), h.days[0].total)
        assertEquals(Trend(100, true), h.trend)
        assertFalse(h.days[14].approx)
    }

    @Test fun merge_daily_never_fills_today_or_days_with_events() {
        val days = listOf(
            DayUsage(today.minusDays(1), true, mapOf("a" to Duration.ofHours(1)), Duration.ofHours(1)),
            DayUsage(today, false, emptyMap(), Duration.ZERO),
        )
        val daily = mapOf(today.minusDays(1) to mapOf("a" to Duration.ofHours(9)), today to mapOf("a" to Duration.ofHours(9)))
        assertEquals(days, UsageHistory.mergeDaily(days, daily, today) { true })
    }

    @Test fun day_with_only_screen_events_has_data_and_zero_total() {
        val h = build(listOf(UsageEvt("", CLOSE_ALL, at(5, 3), "")))
        assertTrue(h.days[9].hasData)
        assertEquals(Duration.ZERO, h.days[9].total)
        assertFalse(h.days[8].hasData)
    }

    @Test fun today_partial() {
        val h = build(listOf(UsageEvt("a", RESUMED, at(0, 14, 0), "A")))
        assertEquals(Duration.ofMinutes(32), h.today.total)
    }

    @Test fun history_caps_open_ended_session_but_fold_does_not() {
        val e = listOf(UsageEvt("a", RESUMED, NOW.minus(Duration.ofHours(10)), "A"))
        assertEquals(Duration.ofHours(6), build(e).today.total)
        assertEquals(Duration.ofHours(10), UsageFolder.fold(e, DAY_START, NOW)["a"])
    }

    @Test fun session_started_before_window() {
        val windowStart = at(14, 0)
        val e = listOf(UsageEvt("a", RESUMED, windowStart.minusSeconds(5 * 3600), "A"), UsageEvt("a", PAUSED, windowStart.plusSeconds(3600), "A"))
        assertEquals(Duration.ofHours(1), build(e).days[0].total)
    }

    @Test fun paused_without_resumed_is_capped() {
        val h = build(listOf(UsageEvt("a", PAUSED, at(5, 10, 0), "A")))
        assertEquals(Duration.ofHours(6), h.days[9].total)
    }

    @Test fun duplicate_resumed_and_multiple_classes() {
        val dup = listOf(
            UsageEvt("a", RESUMED, at(0, 10, 0), "A"), UsageEvt("a", RESUMED, at(0, 10, 5), "A"), UsageEvt("a", PAUSED, at(0, 10, 20), "A"),
        )
        assertEquals(Duration.ofMinutes(20), build(dup).today.total)
        val multi = listOf(
            UsageEvt("a", RESUMED, at(0, 10, 0), "A"), UsageEvt("a", RESUMED, at(0, 10, 10), "B"),
            UsageEvt("a", PAUSED, at(0, 10, 10).plusSeconds(1), "A"), UsageEvt("a", PAUSED, at(0, 10, 30), "B"),
        )
        assertEquals(Duration.ofMinutes(30), build(multi).today.total)
    }

    @Test fun dst_fall_back_day_is_25_hours() {
        val ny = ZoneId.of("America/New_York")
        val d = LocalDate.of(2026, 11, 1) // clocks go back: 25h day
        val s = d.atStartOfDay(ny).toInstant()
        val e2 = d.plusDays(1).atStartOfDay(ny).toInstant()
        val h = UsageHistory.build(
            listOf(UsageEvt("a", RESUMED, s, "A"), UsageEvt("a", PAUSED, e2, "A")),
            d.plusDays(1), e2.plusSeconds(3600), ny, { true },
        )
        assertEquals(Duration.ofHours(25), h.days[13].total)
        assertEquals(Duration.ZERO, h.today.total)
    }

    @Test fun formats() {
        assertEquals("4H 01M", dur(Duration.ofMinutes(241)))
        assertEquals("12M", dur(Duration.ofMinutes(12)))
        assertEquals("0M", dur(Duration.ZERO))
        assertEquals("59M", dur(Duration.ofSeconds(3594)))
        assertEquals("0M", dur(Duration.ofMinutes(-5)))
        assertEquals("25H 00M", dur(Duration.ofHours(25)))
        assertEquals("20M", durMin(20))
        assertEquals("1H 30M", durMin(90))
        assertEquals("23H 12M LEFT", left(Duration.ofHours(23).plusMinutes(12).plusSeconds(5)))
        assertEquals("12M LEFT", left(Duration.ofMinutes(12)))
        assertEquals("<1M LEFT", left(Duration.ofSeconds(30)))
        assertEquals("59M", axisLabel(Duration.ofMinutes(59)))
        assertEquals("1H", axisLabel(Duration.ofMinutes(90)))
        assertNotNull(Trend(1, true))
    }
}
