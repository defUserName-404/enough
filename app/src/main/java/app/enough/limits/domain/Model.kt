package app.enough.limits.domain

import java.time.Instant
import java.time.LocalDate

object Limits {
    const val MIN = 5
    const val MAX = 240
    const val STEP = 5
    fun clamp(m: Int): Int = (m.coerceIn(MIN, MAX) / STEP) * STEP
}

data class Target(val pkg: String, val limitMin: Int)

enum class Phase { SETUP, ACTIVE, RELEASING }

sealed interface Change {
    data class AddOrLower(val target: Target) : Change

    data class RaiseLimit(val target: Target) : Change

    data class Remove(val pkg: String) : Change

    data object Release : Change
}

data class PendingChange(val id: String, val change: Change, val applyAt: Instant)

data class Config(
    val phase: Phase = Phase.SETUP,
    val targets: List<Target> = emptyList(),
    val pending: List<PendingChange> = emptyList(),
    val warned: Map<String, LocalDate> = emptyMap(),
    val failClosedNotified: Boolean = false,
    val unsuspendable: Set<String> = emptySet(),
    /** Packages whose unsuspend failed; retried every tick and included in release. */
    val stuckSuspended: Set<String> = emptySet(),
    val protectionMissing: List<String> = emptyList(),
    val releaseFailed: Boolean = false,
) {
    fun target(pkg: String): Target? = targets.firstOrNull { it.pkg == pkg }
}
