package app.enough.common.domain

import app.enough.limits.domain.Change
import app.enough.limits.domain.Config
import app.enough.limits.domain.EnoughJson
import app.enough.limits.domain.PendingChange
import app.enough.limits.domain.Phase
import app.enough.limits.domain.Target
import app.enough.oem.domain.OemTable
import app.enough.testutil.NOW
import app.enough.usage.domain.AppInfo
import app.enough.usage.domain.PickerFilter
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscCoreTest {
    @Test fun json_roundtrip_all_changes() {
        val cfg = Config(
            phase = Phase.ACTIVE,
            targets = listOf(Target("a", 20)),
            pending = listOf(
                PendingChange("1", Change.AddOrLower(Target("a", 10)), NOW),
                PendingChange("2", Change.RaiseLimit(Target("a", 40)), NOW),
                PendingChange("3", Change.Remove("a"), NOW),
                PendingChange("5", Change.Release, NOW),
            ),
            warned = mapOf("a" to LocalDate.of(2026, 11, 21)),
            failClosedNotified = true, unsuspendable = setOf("x"),
        )
        val s = EnoughJson.encodeToString(Config.serializer(), cfg)
        assertEquals(cfg, EnoughJson.decodeFromString(Config.serializer(), s))
        val withUnknown = s.dropLast(1) + ",\"future\":1}"
        assertEquals(cfg, EnoughJson.decodeFromString(Config.serializer(), withUnknown))
        assertEquals(Config(), EnoughJson.decodeFromString(Config.serializer(), "{}"))
    }

    @Test fun format() {
        assertEquals("12:40", mmss(Duration.ofMinutes(12).plusSeconds(40)))
        assertEquals("240:00", mmss(Duration.ofMinutes(240)))
        assertEquals("23:12:05", hms(Duration.ofHours(23).plusMinutes(12).plusSeconds(5)))
        assertEquals("00:00:00", hms(Duration.ofSeconds(-5)))
        assertEquals("20:00", limitMmss(20))
    }

    @Test fun oem() {
        for (m in listOf("Xiaomi", "Redmi", "POCO")) {
            assertEquals(listOf("autostart", "battery"), OemTable.specsFor(m, "").map { it.id })
        }
        assertEquals(listOf("neversleep"), OemTable.specsFor("samsung", "samsung").map { it.id })
        assertTrue(OemTable.specsFor("Google", "google").isEmpty())
        assertEquals(listOf("startup"), OemTable.specsFor("OnePlus", "OnePlus").map { it.id })
    }

    @Test fun picker_filter() {
        val apps = listOf(AppInfo("p1", "Zed"), AppInfo("p2", "apple"), AppInfo("p3", "Banana"), AppInfo("p4", "Bandcamp"))
        assertEquals(listOf("apple", "Banana", "Zed"), PickerFilter.visible(apps, setOf("p4"), "").map { it.label })
        assertEquals(listOf("Banana", "Bandcamp"), PickerFilter.visible(apps, emptySet(), " BAN ").map { it.label })
    }
}
