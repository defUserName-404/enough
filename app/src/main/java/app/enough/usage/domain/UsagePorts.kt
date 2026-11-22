package app.enough.usage.domain

import java.time.Duration
import java.time.Instant

/** null = no access / unreadable. */
interface UsagePort {
    fun read(dayStart: Instant, now: Instant): UsageSnapshot?
}

data class UsageEvt(val pkg: String, val type: Type, val ts: Instant, val cls: String = "") {
    /** CLOSE_ALL: screen off / shutdown / keyguard shown: every open session ends. */
    /** OPEN_GATE: screen interactive / keyguard hidden (usage counts again). */
    enum class Type { RESUMED, PAUSED, STOPPED, CLOSE_ALL, OPEN_GATE }
}

data class UsageSnapshot(val usage: Map<String, Duration>, val foreground: String?)
