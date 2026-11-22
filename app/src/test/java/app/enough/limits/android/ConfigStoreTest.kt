package app.enough.limits.android

import androidx.datastore.core.CorruptionException
import app.enough.limits.domain.Config
import app.enough.limits.domain.Phase
import app.enough.limits.domain.Target
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val logs = ArrayList<String>()

    private fun main() = File(tmp.root, "datastore/config.json")
    private fun bak() = File(tmp.root, "datastore/config.json.bak")
    private val good = Config(phase = Phase.ACTIVE, targets = listOf(Target("a", 20)))

    /** One app process: DataStore allows a single active instance per file, so each "restart" closes the last. */
    private fun <T> process(block: suspend (ConfigStore) -> T): T = runBlocking {
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            block(ConfigStore(main(), sc) { logs += it })
        } finally {
            sc.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test fun update_writes_backup() {
        process { it.update { good } }
        assertTrue(bak().exists())
        assertEquals(main().readText(), bak().readText())
    }

    @Test fun corrupt_main_is_restored_from_backup() {
        process { it.update { good } }
        main().writeText("{ not json")
        assertEquals(good, process { it.get() })
        assertTrue(logs.any { "restored" in it })
        assertEquals(good, process { it.get() }) // main was rewritten with the good copy
    }

    @Test fun corrupt_main_and_missing_backup_throws_not_empty() {
        process { it.update { good } }
        bak().delete()
        main().writeText("{ not json")
        try { process { it.get() }; fail("must not reset to default") } catch (e: CorruptionException) { }
    }

    @Test fun corrupt_main_and_corrupt_backup_throws() {
        process { it.update { good } }
        bak().writeText("garbage")
        main().writeText("{ not json")
        try { process { it.get() }; fail("must not reset to default") } catch (e: CorruptionException) { }
    }

    @Test fun corrupt_main_never_overwrites_good_backup() {
        process { it.update { good } }
        val before = bak().readText()
        main().writeText("{ not json")
        ConfigBackup(main()).save()
        assertEquals(before, bak().readText())
    }

    @Test fun backup_save_is_noop_without_main() {
        main().parentFile!!.mkdirs()
        ConfigBackup(main()).save()
        assertFalse(bak().exists())
    }

    @Test fun get_seeds_backup_for_existing_installs() {
        process { it.update { good } }
        bak().delete() // pre-upgrade install: main exists, no backup yet
        process { it.get() }
        assertTrue(bak().exists())
    }
}
