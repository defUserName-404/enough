package dev.clamp.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/** Resumable, idempotent release. Config is reset only when the device release fully succeeded. */
class Releaser(private val cfg: ConfigPort, private val device: DevicePort) {
    private val mutex = Mutex()

    suspend fun attempt(): Boolean = mutex.withLock {
        val c = cfg.update { it.copy(phase = Phase.RELEASING) }
        val pkgs = (c.targets.map { it.pkg } + c.unsuspendable + c.stuckSuspended).toSet()
        val ok = try {
            device.release(pkgs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (ok) cfg.update { Config() } else cfg.update { it.copy(releaseFailed = true) }
        ok
    }
}
