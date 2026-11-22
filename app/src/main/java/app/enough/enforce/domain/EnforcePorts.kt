package app.enough.enforce.domain

import java.time.Duration

/**
 * [refused]: packages the OS returned in its failed list (permanent-ish).
 * [threw]: the call threw; everything is a transient failure, retry next tick.
 */
data class SuspendResult(val refused: List<String> = emptyList(), val threw: Boolean = false)

interface DevicePort {
    fun isOwner(): Boolean
    fun setSuspended(pkgs: Collection<String>, suspended: Boolean): SuspendResult

    /** Applies and verifies the restriction set. Returns the labels of what is still missing. */
    fun applyProtection(): List<String>

    /** True only if nothing is still suspended AND device owner is cleared. */
    fun release(pkgs: Collection<String>): Boolean
}

interface NotifyPort {
    fun warn(pkg: String, left: Duration)
    fun failClosed(show: Boolean)
    fun lostOwner()
    fun configError()
}
