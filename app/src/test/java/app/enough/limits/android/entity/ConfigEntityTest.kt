package app.enough.limits.android.entity

import app.enough.limits.android.decodeConfig
import app.enough.limits.android.encodeConfig
import app.enough.limits.domain.Change
import app.enough.limits.domain.Config
import app.enough.limits.domain.PendingChange
import app.enough.limits.domain.Phase
import app.enough.limits.domain.Target
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigEntityTest {
    private val at = Instant.parse("2026-11-21T14:32:00Z")

    /** Same values that were encoded into config-v1.json by the pre-entity serializer. */
    private val v1 = Config(
        phase = Phase.ACTIVE,
        targets = listOf(Target("com.a", 20), Target("com.b", 45)),
        pending = listOf(
            PendingChange("1", Change.AddOrLower(Target("com.a", 10)), at),
            PendingChange("2", Change.RaiseLimit(Target("com.b", 60)), at),
            PendingChange("3", Change.Remove("com.a"), at),
            PendingChange("4", Change.Release, at),
        ),
        warned = mapOf("com.a" to LocalDate.of(2026, 11, 21)),
        failClosedNotified = true,
        unsuspendable = setOf("com.x"),
        stuckSuspended = setOf("com.y"),
        protectionMissing = listOf("no_factory_reset"),
        releaseFailed = true,
    )

    private val golden = File("src/test/resources/config-v1.json").readText().trim()

    private fun tree(s: String): JsonElement = Json.parseToJsonElement(s)

    @Test fun golden_v1_decodes_to_same_domain_config() {
        assertEquals(v1, decodeConfig(golden))
    }

    @Test fun golden_v1_reencodes_identically() {
        assertEquals(tree(golden), tree(encodeConfig(decodeConfig(golden))))
        assertEquals(tree(golden), tree(encodeConfig(v1)))
    }

    @Test fun unknown_fields_and_empty_document() {
        assertEquals(v1, decodeConfig(golden.dropLast(1) + ",\"future\":1}"))
        assertEquals(Config(), decodeConfig("{}"))
    }
}
