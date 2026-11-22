@file:UseSerializers(InstantEpochMs::class, LocalDateIso::class)

package app.enough.limits.android.entity

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

/*
 * On-disk shape of config.json. Field names, defaults, @SerialName values and the enum
 * names are the stored format: changing any of them breaks configs already on phones.
 * Guarded by ConfigEntityTest against src/test/resources/config-v1.json.
 */

@Serializable
data class TargetEntity(val pkg: String, val limitMin: Int)

@Serializable
enum class PhaseEntity { SETUP, ACTIVE, RELEASING }

@Serializable
sealed interface ChangeEntity {
    @Serializable @SerialName("addOrLower")
    data class AddOrLower(val target: TargetEntity) : ChangeEntity

    @Serializable @SerialName("raise")
    data class RaiseLimit(val target: TargetEntity) : ChangeEntity

    @Serializable @SerialName("remove")
    data class Remove(val pkg: String) : ChangeEntity

    @Serializable @SerialName("release")
    data object Release : ChangeEntity
}

@Serializable
data class PendingChangeEntity(val id: String, val change: ChangeEntity, val applyAt: Instant)

@Serializable
data class ConfigEntity(
    val phase: PhaseEntity = PhaseEntity.SETUP,
    val targets: List<TargetEntity> = emptyList(),
    val pending: List<PendingChangeEntity> = emptyList(),
    val warned: Map<String, LocalDate> = emptyMap(),
    val failClosedNotified: Boolean = false,
    val unsuspendable: Set<String> = emptySet(),
    val stuckSuspended: Set<String> = emptySet(),
    val protectionMissing: List<String> = emptyList(),
    val releaseFailed: Boolean = false,
)

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
