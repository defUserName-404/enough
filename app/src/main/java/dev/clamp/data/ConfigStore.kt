package dev.clamp.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import dev.clamp.core.ClampJson
import dev.clamp.core.Config
import dev.clamp.core.ConfigPort
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException

object ConfigSerializer : Serializer<Config> {
    override val defaultValue = Config()

    override suspend fun readFrom(input: InputStream): Config = try {
        ClampJson.decodeFromString(Config.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("config.json unreadable", e)
    }

    override suspend fun writeTo(t: Config, output: OutputStream) {
        output.write(ClampJson.encodeToString(Config.serializer(), t).encodeToByteArray())
    }
}

private val Context.configDataStore by dataStore(fileName = "config.json", serializer = ConfigSerializer)

/**
 * Credential-encrypted storage (default). Never touch before UserManager.isUserUnlocked.
 * No corruption handler on purpose: a corrupt file must not silently reset to empty (fail open).
 * TODO(device): decide corruption UX
 */
class ConfigStore(private val ctx: Context) : ConfigPort {
    val flow: Flow<Config> get() = ctx.applicationContext.configDataStore.data
    override suspend fun get(): Config = flow.first()
    override suspend fun update(f: suspend (Config) -> Config): Config =
        ctx.applicationContext.configDataStore.updateData(f)
}
