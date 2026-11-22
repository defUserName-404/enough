package app.enough.common.domain

import app.enough.oem.domain.OemTable
import app.enough.usage.domain.AppInfo
import app.enough.usage.domain.PickerFilter
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscCoreTest {
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
