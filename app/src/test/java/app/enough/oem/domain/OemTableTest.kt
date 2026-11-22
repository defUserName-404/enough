package app.enough.oem.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemTableTest {
    @Test fun xiaomi_family_gets_autostart_and_battery() {
        for ((m, b) in listOf("Xiaomi" to "Xiaomi", "Xiaomi" to "Redmi", "Xiaomi" to "POCO", "xiaomi" to "")) {
            val ids = OemTable.specsFor(m, b).map { it.id }
            assertEquals("$m/$b", listOf("autostart", "battery"), ids)
        }
    }

    @Test fun autostart_is_required_not_best_effort() {
        val autostart = OemTable.specsFor("Xiaomi", "Redmi").first { it.id == "autostart" }
        assertTrue(autostart.components.isNotEmpty())
        assertEquals(false, autostart.bestEffort)
    }

    @Test fun emulator_has_no_oem_steps() {
        assertEquals(emptyList<OemSpec>(), OemTable.specsFor("Google", "google"))
    }
}
