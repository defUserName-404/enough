@file:UseSerializers(InstantEpochMs::class, LocalDateIso::class)

package app.enough.limits.domain

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

object Limits {
    const val MIN = 5
    const val MAX = 240
    const val STEP = 5
    fun clamp(m: Int): Int = (m.coerceIn(MIN, MAX) / STEP) * STEP
}

@Serializable
data class Target(val pkg: String, val limitMin: Int)

@Serializable
enum class Phase { SETUP, ACTIVE, RELEASING }

@Serializable
sealed interface Change {
    @Serializable @SerialName("addOrLower")
    data class AddOrLower(val target: Target) : Change

    @Serializable @SerialName("raise")
    data class RaiseLimit(val target: Target) : Change

    @Serializable @SerialName("remove")
    data class Remove(val pkg: String) : Change

    @Serializable @SerialName("release")
    data object Release : Change
}

@Serializable
data class PendingChange(val id: String, val change: Change, val applyAt: Instant)

@Serializable
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

object InstantEpochMs : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilli())
    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())
}

object LocalDateIso : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

val EnoughJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}
