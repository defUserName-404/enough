package app.enough.setup.domain

import java.io.File
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopbackGuardTest {
    @Test fun host_is_loopback() {
        assertEquals("127.0.0.1", AdbSetup.HOST)
        assertTrue(InetAddress.getByName(AdbSetup.HOST).isLoopbackAddress)
    }

    @Test fun source_scan() {
        val root = File("src/main/java")
        assertTrue("src/main/java not found from ${File(".").absolutePath}", root.exists())
        val ip = Regex("""\b\d{1,3}(\.\d{1,3}){3}\b""")
        val banned = listOf(
            "\"localhost\"", "URL(", "HttpURLConnection", "Socket", "DatagramSocket", "InetAddress", "okhttp",
        )
        val bad = ArrayList<String>()
        var scanned = 0
        root.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            scanned++
            val text = f.readText()
            ip.findAll(text).forEach { if (it.value != "127.0.0.1") bad += "${f.name}: ${it.value}" }
            // The updater is the one file allowed to use HTTPS, and only towards GitHub.
            val isUpdater = f.name == "Updater.kt"
            val bannedHere = if (isUpdater) listOf("\"localhost\"", "DatagramSocket", "InetAddress", "okhttp") else banned
            bannedHere.forEach { if (it in text) bad += "${f.name}: $it" }
            if (isUpdater) {
                Regex("""https?://[^/"\s]+""").findAll(text).forEach {
                    if (it.value != "https://github.com") bad += "${f.name}: ${it.value}"
                }
                if ("http://" in text) bad += "${f.name}: plain http"
            }
            val isClient = f.name == "AdbClient.kt"
            if (!isClient) {
                if ("setHostAddress" in text) bad += "${f.name}: setHostAddress"
                // The runner only calls the AdbPort interface.
                if (f.name != "SetupRunner.kt" && Regex("""\.connect\(""").containsMatchIn(text)) bad += "${f.name}: .connect("
            } else {
                Regex("""\.(pair|connect)\(([^,)]*)""").findAll(text).forEach {
                    if (it.groupValues[2].trim() != "AdbSetup.HOST") bad += "${f.name}: ${it.value}"
                }
                Regex("""setHostAddress\(([^)]*)\)""").findAll(text).forEach {
                    if (it.groupValues[1].trim() != "AdbSetup.HOST") bad += "${f.name}: ${it.value}"
                }
            }
        }
        assertTrue("no sources scanned", scanned > 10)
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
