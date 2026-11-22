package app.enough

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer rules:
 * - `<feature>/domain/` is pure Kotlin: no Android, and no reach into `android/` or `ui/`.
 *   This is what keeps the domain testable on the JVM.
 * - `ui/` composables (everything except `*Route.kt` and `*ViewModel.kt`) only render state:
 *   no [Graph], Controller, app object or `android/` adapters. Routes wire a ViewModel to them.
 */
class ArchitectureTest {
    private val src = File("src/main/java/app/enough")
    private val banned = listOf(
        Regex("""^import android\."""),
        Regex("""^import androidx\."""),
        Regex("""^import app\.enough\.[\w.]*\.(android|ui)\."""),
        Regex("""^import app\.enough\.R\b"""),
    )

    private val uiBanned = listOf(
        Regex("""^import app\.enough\.(Graph|Controller|EnoughApp|rememberGraph)\b"""),
        Regex("""^import app\.enough\.[\w.]*\.android\."""),
    )

    private fun inLayer(f: File, layer: String) = layer in f.relativeTo(src).path.split(File.separator)

    private fun violations(files: List<File>, rules: List<Regex>) = files.flatMap { f ->
        f.readLines().mapIndexedNotNull { i, line ->
            if (rules.any { it.containsMatchIn(line.trim()) }) "${f.relativeTo(src)}:${i + 1}: ${line.trim()}" else null
        }
    }

    @Test
    fun uiComposablesOnlyRenderState() {
        val uiFiles = src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && inLayer(it, "ui") }
            .filterNot { it.name.endsWith("Route.kt") || it.name.endsWith("ViewModel.kt") }
            .toList()
        assertTrue("no ui files found", uiFiles.isNotEmpty())
        val v = violations(uiFiles, uiBanned)
        assertEquals("ui composable reaches past its ViewModel:\n" + v.joinToString("\n"), emptyList<String>(), v)
    }

    @Test
    fun domainPackagesArePure() {
        assertTrue("run from the app module: ${src.absolutePath}", src.isDirectory)
        val domainFiles = src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && inLayer(it, "domain") }
            .toList()
        assertTrue("no domain files found", domainFiles.isNotEmpty())

        val v = violations(domainFiles, banned)
        assertEquals("domain imports Android/UI:\n" + v.joinToString("\n"), emptyList<String>(), v)
    }
}
