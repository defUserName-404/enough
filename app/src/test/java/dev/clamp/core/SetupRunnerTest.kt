package dev.clamp.core

import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupRunnerTest {
    class FakeEnv : SetupEnvPort {
        var adbOn = true
        var owner = false
        override fun wifi() = true
        override fun adbWifi() = adbOn
        override fun localHosts() = setOf("192.168.1.23")
        override fun canNotify() = true
        override fun isOwner() = owner
    }
    class FakeAdb(private val env: FakeEnv) : AdbPort {
        val calls = mutableListOf<String>()
        var connectOk = true
        var connectThrows = false
        var offWorks = true
        var pairResults = ArrayDeque<Boolean>()
        var dpmOut = "Success: Device owner set to package x"
        var dpmHangs = false
        override suspend fun pair(port: Int, code: String): Result<Unit> {
            calls += "pair($port,$code)"
            return if (pairResults.removeFirstOrNull() ?: true) Result.success(Unit) else Result.failure(RuntimeException("rejected"))
        }
        override suspend fun connect(port: Int): Result<Unit> {
            calls += "connect($port)"
            if (connectThrows) throw IllegalStateException("adb exploded")
            return if (connectOk) Result.success(Unit) else Result.failure(RuntimeException("no"))
        }
        override suspend fun shell(cmd: String, timeout: Duration): Result<String> {
            calls += "shell($cmd)"
            if (cmd == AdbSetup.DPM_CMD) {
                if (dpmHangs) awaitCancellation()
                return Result.success(dpmOut)
            }
            if (cmd == AdbSetup.ADB_WIFI_OFF && offWorks) env.adbOn = false
            return Result.success("")
        }
        override fun close() { calls += "close" }
        override fun deleteKeys() { calls += "deleteKeys" }
    }
    class FakeDisco : DiscoveryPort {
        val pairing = MutableStateFlow<List<MdnsService>>(emptyList())
        val connect = MutableStateFlow<List<MdnsService>>(emptyList())
        var failWith: Exception? = null
        override fun services(type: String): Flow<List<MdnsService>> {
            failWith?.let { ex -> return flow { throw ex } }
            return if (type == AdbSetup.PAIRING_TYPE) pairing else connect
        }
    }

    private val env = FakeEnv()
    private val adb = FakeAdb(env)
    private val disco = FakeDisco()
    private val codes = Channel<String>(Channel.UNLIMITED)
    private val views = mutableListOf<SetupView>()
    private val runner = SetupRunner(adb, disco, env, codes) { views += it }

    private fun svc(p: Int, seq: Long = 1) = listOf(MdnsService("s$p", p, "127.0.0.1", seq))

    private suspend fun kotlinx.coroutines.test.TestScope.toDiscovery() {
        advanceTimeBy(AdbSetup.FAST_PATH.toMillis() + 1); runCurrent()
    }

    @Test fun happy_path_order() = runTest {
        val job = async { runner.run() }
        toDiscovery()
        disco.pairing.value = svc(37145); runCurrent()
        codes.send("482915"); runCurrent()
        disco.connect.value = svc(41877, 2); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(SetupState.Done, job.await())
        assertEquals(
            listOf(
                "pair(37145,482915)", "connect(41877)", "shell(${AdbSetup.DPM_CMD})", "deleteKeys",
                "shell(${AdbSetup.ADB_WIFI_OFF})", "close",
            ),
            adb.calls,
        )
        val states = views.map { it.state }
        assertTrue(states.indexOf(SetupState.DisablingAdb) < states.indexOf(SetupState.Done))
    }

    @Test fun accounts_failure_keeps_keys() = runTest {
        adb.dpmOut = "Not allowed to set the device owner because there are already some accounts on the device"
        val job = async { runner.run() }
        toDiscovery()
        disco.pairing.value = svc(1); runCurrent()
        codes.send("123456"); runCurrent()
        disco.connect.value = svc(2, 2); runCurrent()
        val r = job.await()
        assertTrue(r is SetupState.Failed && r.why == SetupFailure.ACCOUNTS_PRESENT)
        assertFalse("deleteKeys" in adb.calls)
        assertEquals("close", adb.calls.last())
    }

    @Test fun timeout_without_pairing_service() = runTest {
        val job = async { runner.run() }
        advanceTimeBy(Duration.ofMinutes(5).toMillis() + 1000); runCurrent()
        val r = job.await()
        assertTrue(r is SetupState.Failed && r.why == SetupFailure.TIMEOUT)
        assertTrue("close" in adb.calls)
    }

    @Test fun fast_path_skips_pairing() = runTest {
        disco.connect.value = svc(7)
        val job = async { runner.run() }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(SetupState.Done, job.await())
        assertFalse(adb.calls.any { it.startsWith("pair") })
    }

    @Test fun connect_failures_three_attempts() = runTest {
        adb.connectOk = false
        val job = async { runner.run() }
        toDiscovery()
        disco.pairing.value = svc(1); runCurrent()
        codes.send("123456"); runCurrent()
        disco.connect.value = svc(2, 2)
        advanceTimeBy(10_000); runCurrent()
        val r = job.await()
        assertTrue(r is SetupState.Failed && r.why == SetupFailure.CONNECT_FAILED)
        assertEquals(3, adb.calls.count { it.startsWith("connect") })
    }

    @Test fun discovery_failure_is_failed_unknown_not_crash() = runTest {
        disco.failWith = IllegalStateException("mDNS discovery failed (0)")
        val r = runner.run()
        assertTrue(r is SetupState.Failed && r.why == SetupFailure.UNKNOWN && "mDNS" in r.raw)
        assertTrue("close" in adb.calls)
    }

    @Test fun adb_throw_is_failed_unknown() = runTest {
        adb.connectThrows = true
        disco.connect.value = svc(7)
        val r = async { runner.run() }
        advanceTimeBy(5_000); runCurrent()
        val s = r.await()
        assertTrue(s is SetupState.Failed && s.why == SetupFailure.UNKNOWN)
    }

    @Test fun unknown_output_with_owner_is_success() = runTest {
        adb.dpmOut = "weird output"
        env.owner = true
        disco.connect.value = svc(7)
        val job = async { runner.run() }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(SetupState.Done, job.await())
        assertTrue("deleteKeys" in adb.calls)
    }

    @Test fun adb_off_failure_leads_to_still_on() = runTest {
        adb.offWorks = false
        disco.connect.value = svc(7)
        val job = async { runner.run() }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(SetupState.DoneAdbStillOn, job.await())
        assertEquals(2, adb.calls.count { it == "shell(${AdbSetup.ADB_WIFI_OFF})" }) // retried once
        assertFalse(SetupMachine.canActivate(SetupState.DoneAdbStillOn) { env.adbOn })
    }

    @Test fun timeout_in_provisioning_when_owner_still_turns_adb_off() = runTest {
        adb.dpmHangs = true
        env.owner = true
        disco.connect.value = svc(7)
        val job = async { runner.run() }
        advanceTimeBy(Duration.ofMinutes(5).toMillis() + 5_000); runCurrent()
        assertEquals(SetupState.Done, job.await())
        assertTrue("deleteKeys" in adb.calls)
        assertTrue("shell(${AdbSetup.ADB_WIFI_OFF})" in adb.calls)
    }

    @Test fun timeout_in_provisioning_not_owner_fails() = runTest {
        adb.dpmHangs = true
        disco.connect.value = svc(7)
        val job = async { runner.run() }
        advanceTimeBy(Duration.ofMinutes(5).toMillis() + 5_000); runCurrent()
        val r = job.await()
        assertTrue(r is SetupState.Failed && r.why == SetupFailure.TIMEOUT)
        assertFalse("deleteKeys" in adb.calls)
    }

    @Test fun cancellation_closes_and_emits_nothing_after() = runTest {
        val job = async { runner.run() }
        toDiscovery()
        val n = views.size
        job.cancel()
        runCurrent()
        assertTrue(job.isCancelled)
        assertEquals(n, views.size)
        assertTrue("close" in adb.calls)
    }

    @Test fun pair_retry_after_rejection_needs_new_port_or_fresh_emission() = runTest {
        adb.pairResults = ArrayDeque(listOf(false, true))
        val job = async { runner.run() }
        toDiscovery()
        disco.pairing.value = svc(100); runCurrent()
        codes.send("111111"); runCurrent()
        // rejected: stale port 100 must not bring us back to AwaitingCode
        assertEquals(SetupState.Discovering, views.last().state)
        disco.pairing.value = svc(100, 5); runCurrent()
        assertEquals(SetupState.Discovering, views.last().state)
        // NSD reports it lost, then a fresh service with a different port appears
        disco.pairing.value = emptyList(); runCurrent()
        disco.pairing.value = svc(200, 6); runCurrent()
        assertEquals(SetupState.AwaitingCode(200), views.last().state)
        codes.send("222222"); runCurrent()
        disco.connect.value = svc(300, 7); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(SetupState.Done, job.await())
        assertEquals(listOf("pair(100,111111)", "pair(200,222222)"), adb.calls.filter { it.startsWith("pair") })
    }
}
