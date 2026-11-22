package app.enough.testutil

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

val NOW: Instant = Instant.parse("2026-11-21T14:32:00Z")
val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
val DAY_START: Instant = Instant.parse("2026-11-21T00:00:00Z")

fun t(h: Int, m: Int, s: Int = 0): Instant = DAY_START.plusSeconds(h * 3600L + m * 60L + s)

fun clockAt(i: Instant): Clock = Clock.fixed(i, ZoneOffset.UTC)
