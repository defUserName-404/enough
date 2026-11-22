package app.enough.setup.android

import android.content.Context
import android.os.Build
import app.enough.setup.domain.AdbPort
import app.enough.setup.domain.AdbSetup
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

// Unverified on MIUI/HyperOS (release-smoke-test.md section F): whether "USB debugging (Security settings)" is needed for dpm over
// wireless adb (-> NEEDS_SECURITY_SETTINGS / WITH A COMPUTER), and conscrypt-android as the TLS provider.
/** AdbPort over libadb. No host parameter anywhere: every socket targets AdbSetup.HOST. */
class AdbClient(private val ctx: Context) : AdbPort {
    private val keys = AdbKeys(File(ctx.noBackupFilesDir, "adb"))

    private class Mgr(val k: PrivateKey, val c: Certificate) : AbsAdbConnectionManager() {
        init {
            setApi(Build.VERSION.SDK_INT)
            setHostAddress(AdbSetup.HOST)
            setTimeout(10, TimeUnit.SECONDS)
        }
        override fun getPrivateKey() = k
        override fun getCertificate() = c
        override fun getDeviceName() = "enough"
    }

    private var mgr: Mgr? = null

    @Synchronized
    private fun mgr(): Mgr {
        mgr?.let { return it }
        ensureProvider()
        val (k, c) = keys.loadOrCreate()
        return Mgr(k, c).also { mgr = it }
    }

    /**
     * libadb's calls block and ignore coroutine timeouts. Run them on a thread; on cancellation
     * close the manager (unblocks the socket) and interrupt, so withTimeout really fires.
     */
    private suspend fun <T> blocking(block: () -> T): T = suspendCancellableCoroutine { cont ->
        val t = thread(isDaemon = true, name = "enough-adb") {
            try {
                cont.resume(block())
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }
        cont.invokeOnCancellation {
            close()
            t.interrupt()
        }
    }

    private suspend fun <T> safe(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pair(port: Int, code: String): Result<Unit> = safe {
        check(blocking { mgr().pair(AdbSetup.HOST, port, code) }) { "pairing rejected" }
    }

    override suspend fun connect(port: Int): Result<Unit> = safe {
        check(blocking { mgr().connect(AdbSetup.HOST, port) }) { "connect failed" }
    }

    override suspend fun shell(cmd: String, timeout: Duration): Result<String> = safe {
        withTimeoutOrNull(timeout.toMillis()) {
            blocking {
                mgr().openStream("shell:$cmd").use { s -> s.openInputStream().readBytes().decodeToString() }
            }
        } ?: error("shell timed out")
    }

    @Synchronized
    override fun close() {
        runCatching { mgr?.close() }
        mgr = null
    }

    override fun deleteKeys() {
        keys.delete()
    }

    private companion object {
        @Synchronized
        fun ensureProvider() {
            if (Security.getProvider("Conscrypt") == null) {
                Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1)
            }
        }
    }
}
