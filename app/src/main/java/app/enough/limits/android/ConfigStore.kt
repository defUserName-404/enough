package app.enough.limits.android

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import app.enough.limits.domain.Config
import app.enough.limits.domain.ConfigPort
import app.enough.limits.domain.EnoughJson
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

object ConfigSerializer : Serializer<Config> {
    override val defaultValue = Config()

    override suspend fun readFrom(input: InputStream): Config = try {
        EnoughJson.decodeFromString(Config.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("config.json unreadable", e)
    }

    override suspend fun writeTo(t: Config, output: OutputStream) {
        output.write(EnoughJson.encodeToString(Config.serializer(), t).encodeToByteArray())
    }
}

/**
 * Last-known-good copy of config.json (`config.json.bak`, same directory, same encryption).
 * Only a file that decodes is ever copied, so a corrupt write cannot poison the backup.
 * Used solely when the main file is corrupt; if the backup is bad too the original error
 * surfaces and the app stays fail-closed (never a silent reset to empty).
 */
class ConfigBackup(private val main: File) {
    private val bak = File(main.parentFile, main.name + ".bak")
    private val tmp = File(main.parentFile, main.name + ".bak.tmp")

    private fun decodes(bytes: ByteArray): Boolean = try {
        EnoughJson.decodeFromString(Config.serializer(), bytes.decodeToString()); true
    } catch (e: SerializationException) {
        false
    }

    @Synchronized
    fun save() {
        val bytes = try { main.readBytes() } catch (e: IOException) { return }
        if (!decodes(bytes)) return
        if (bak.exists() && bak.readBytes().contentEquals(bytes)) return
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), bak.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    @Synchronized
    fun restore(cause: CorruptionException): Config = try {
        EnoughJson.decodeFromString(Config.serializer(), bak.readBytes().decodeToString())
    } catch (e: IOException) {
        cause.addSuppressed(e); throw cause
    } catch (e: SerializationException) {
        cause.addSuppressed(e); throw cause
    }
}

/**
 * Credential-encrypted storage (default). Never touch before UserManager.isUserUnlocked.
 * No silent reset on corruption (fail open): a corrupt file is replaced only by the last-known-good
 * backup, and otherwise the read throws so callers stay fail-closed (error screen + notification).
 */
class ConfigStore(
    file: File,
    scope: CoroutineScope,
    private val log: (String) -> Unit = {},
) : ConfigPort {
    constructor(ctx: Context) : this(
        File(ctx.applicationContext.filesDir, "datastore/config.json"), // same path the old delegate used
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        { Log.w("Enough", it) },
    )

    private val backup = ConfigBackup(file)
    private val seeded = AtomicBoolean(false)
    private val store: DataStore<Config> = DataStoreFactory.create(
        serializer = ConfigSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { ex ->
            backup.restore(ex).also { log("config.json corrupt, restored from backup") }
        },
        scope = scope,
        produceFile = { file },
    )

    val flow: Flow<Config> get() = store.data

    // Installs from before the backup existed get one on first successful read.
    override suspend fun get(): Config = flow.first().also { if (seeded.compareAndSet(false, true)) saveBackup() }

    override suspend fun update(f: suspend (Config) -> Config): Config = store.updateData(f).also { saveBackup() }

    // A failed backup must never fail the update itself.
    private suspend fun saveBackup() = withContext(Dispatchers.IO) {
        try { backup.save() } catch (e: CancellationException) { throw e } catch (e: Exception) { log("config backup failed: $e") }
    }
}
