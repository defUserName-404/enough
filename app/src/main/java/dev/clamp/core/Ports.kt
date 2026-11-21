package dev.clamp.core

import java.time.Duration
import java.time.Instant

interface ConfigPort {
    suspend fun get(): Config
    suspend fun update(f: suspend (Config) -> Config): Config
}

/** null = no access / unreadable. */
interface UsagePort {
    fun read(dayStart: Instant, now: Instant): UsageSnapshot?
}

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

/** Every socket targets [AdbSetup.HOST]. */
interface AdbPort {
    suspend fun pair(port: Int, code: String): Result<Unit>
    suspend fun connect(port: Int): Result<Unit>

    /** stdout+stderr until EOF. */
    suspend fun shell(cmd: String, timeout: Duration): Result<String>
    fun close()
    fun deleteKeys()
}

/** Cold; cancelling the collector stops discovery. */
interface DiscoveryPort {
    fun services(type: String): kotlinx.coroutines.flow.Flow<List<MdnsService>>
}

interface SetupEnvPort {
    fun wifi(): Boolean
    fun adbWifi(): Boolean
    fun localHosts(): Set<String>
    fun canNotify(): Boolean
    fun isOwner(): Boolean
}
