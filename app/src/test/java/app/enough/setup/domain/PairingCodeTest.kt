package app.enough.setup.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingCodeTest {
    @Test fun normalize() {
        assertEquals("482915", PairingCode.normalize("482915"))
        assertEquals("482915", PairingCode.normalize(" 482 915\n"))
        for (bad in listOf("48291", "4829150", "48291a", "", "４８２９１５")) assertNull(bad, PairingCode.normalize(bad))
        assertNull(PairingCode.normalize(null))
    }
}
