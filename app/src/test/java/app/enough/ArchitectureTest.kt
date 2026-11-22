package app.enough

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every `<feature>/domain/` package is pure Kotlin: no Android, and no reach into a
 * feature's `android/` or `ui/` layer. This is what keeps the domain testable on the JVM.
 */
class ArchitectureTest {
    private val src = File("src/main/java/app/enough")
    private val banned = listOf(
        Regex("""^import android\."""),
        Regex("""^import androidx\."""),
        Regex("""^import app\.enough\.[\w.]*\.(android|ui)\."""),
        Regex("""^import app\.enough\.R\b"""),
    )

    @Test
    fun domainPackagesArePure() {
        assertTrue("run from the app module: ${src.absolutePath}", src.isDirectory)
        val domainFiles = src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "domain" in it.relativeTo(src).path.split(File.separator) }
            .toList()
        assertTrue("no domain files found", domainFiles.isNotEmpty())

        val violations = domainFiles.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if (banned.any { it.containsMatchIn(line.trim()) }) "${f.relativeTo(src)}:${i + 1}: ${line.trim()}" else null
            }
        }
        assertEquals("domain imports Android/UI:\n" + violations.joinToString("\n"), emptyList<String>(), violations)
    }
}
