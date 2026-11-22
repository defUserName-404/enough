package app.enough.setup.domain

import java.time.Duration
import kotlinx.coroutines.flow.Flow

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
    fun services(type: String): Flow<List<MdnsService>>
}

interface SetupEnvPort {
    fun wifi(): Boolean
    fun adbWifi(): Boolean
    fun localHosts(): Set<String>
    fun canNotify(): Boolean
    fun isOwner(): Boolean
}
