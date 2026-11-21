package app.enough.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import app.enough.BuildConfig
import app.enough.core.ReleaseInfo
import app.enough.core.UpdateCheck
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * The only networking in the app besides the loopback adb client. Runs only when the user taps
 * CHECK or UPDATE, and only talks to GitHub over HTTPS. Android itself refuses an update that is
 * not signed with the same key as the installed app.
 */
class Updater(private val ctx: Context) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    fun setState(s: UpdateState) { _state.value = s }

    suspend fun check() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        _state.value = withContext(Dispatchers.IO) {
            try {
                val info = UpdateCheck.parseLatest(getText(LATEST))
                when {
                    info == null -> UpdateState.Failed("No release found.")
                    UpdateCheck.isNewer(BuildConfig.VERSION_NAME, info.version) -> UpdateState.Available(info)
                    else -> UpdateState.UpToDate
                }
            } catch (e: Exception) {
                UpdateState.Failed(netMessage(e))
            }
        }
    }

    suspend fun install(info: ReleaseInfo) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        _state.value = UpdateState.Downloading(0)
        _state.value = withContext(Dispatchers.IO) {
            try {
                val want = UpdateCheck.parseSha256(getText(info.sha256Url)) ?: return@withContext UpdateState.Failed("Bad checksum file.")
                val apk = download(info.apkUrl, want)
                val archive = ctx.packageManager.getPackageArchiveInfo(apk.path, 0)
                if (archive?.packageName != ctx.packageName) {
                    apk.delete()
                    return@withContext UpdateState.Failed("This build can't be updated from GitHub. Install the release APK once.")
                }
                commit(apk)
                UpdateState.Installing
            } catch (e: Exception) {
                UpdateState.Failed(netMessage(e))
            }
        }
    }

    private fun commit(apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            apk.inputStream().use { input ->
                s.openWrite("enough.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    s.fsync(out)
                }
            }
            val intent = Intent(ctx, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION)
            val pending = PendingIntent.getBroadcast(ctx, id, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            s.commit(pending.intentSender)
        }
    }

    private fun download(url: String, expectedSha256: String): File {
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "enough.apk")
        val c = open(url)
        val total = c.contentLengthLong
        val md = MessageDigest.getInstance("SHA-256")
        c.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    md.update(buf, 0, n)
                    done += n
                    if (total > 0) _state.value = UpdateState.Downloading((done * 100 / total).toInt())
                }
            }
        }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        if (got != expectedSha256) {
            out.delete()
            throw IllegalStateException("Download is corrupted (checksum mismatch).")
        }
        return out
    }

    private fun getText(url: String): String = open(url).inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "https only" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Enough-Updater")
        c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
        if (c.responseCode !in 200..299) throw IllegalStateException("GitHub answered ${c.responseCode}.")
        return c
    }

    private fun netMessage(e: Exception): String = when (e) {
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException -> "No internet connection."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Update failed."
    }

    companion object {
        private const val LATEST = "https://api.github.com/repos/defUserName-404/enough/releases/latest"
    }
}
