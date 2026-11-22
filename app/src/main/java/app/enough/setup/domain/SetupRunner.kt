package app.enough.setup.domain

import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Pure orchestrator for the on-device DO provisioning. Tested with fakes. */
class SetupRunner(
    private val adb: AdbPort,
    private val disco: DiscoveryPort,
    private val env: SetupEnvPort,
    private val codes: ReceiveChannel<String>,
    private val emit: (SetupView) -> Unit,
) {
    companion object {
        val ADB_OFF_SETTLE: Duration = Duration.ofMillis(500)
    }

    private var view = SetupView(SetupState.Idle, listOf(SetupMachine.line(SetupState.Idle)))
    private val state get() = view.state

    private fun send(e: SetupEvent) {
        view = SetupMachine.reduce(view, e)
        emit(view)
    }

    private fun pickFlow(type: String): Flow<MdnsService> =
        disco.services(type).map { ServicePick.pick(it, env.localHosts()) }.filterNotNull()
            .distinctUntilChanged { a, b -> a.port == b.port }

    private suspend fun firstPick(type: String): MdnsService = pickFlow(type).first()

    private suspend fun connectWithRetry(port: Int): Boolean {
        repeat(AdbSetup.CONNECT_TRIES) { i ->
            if (adb.connect(port).isSuccess) return true
            if (i < AdbSetup.CONNECT_TRIES - 1) delay(AdbSetup.CONNECT_RETRY.toMillis())
        }
        return false
    }

    suspend fun run(): SetupState {
        try {
            withTimeout(AdbSetup.SESSION.toMillis()) { body() }
        } catch (e: TimeoutCancellationException) {
            if (SetupMachine.holdsActivation(state) && env.isOwner()) recover() else send(SetupEvent.Timeout)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (SetupMachine.holdsActivation(state) && env.isOwner()) recover()
            else send(SetupEvent.Error(e.message ?: e.javaClass.simpleName))
        } finally {
            withContext(NonCancellable) { adb.close() }
        }
        return state
    }

    /** Device owner is already set: finish the job (delete keys, adb off) even though the session broke. */
    private suspend fun recover() = withContext(NonCancellable) {
        if (state is SetupState.Provisioning) send(SetupEvent.DpmOutput("", true))
        if (state is SetupState.DisablingAdb) disableAdb()
    }

    private suspend fun disableAdb() {
        adb.deleteKeys() // the key stays in memory for this open connection
        var off = false
        repeat(2) {
            if (!off) {
                adb.shell(AdbSetup.ADB_WIFI_OFF, Duration.ofSeconds(5)) // result alone is unreliable: adbd drops the link
                delay(ADB_OFF_SETTLE.toMillis())
                off = !env.adbWifi()
            }
        }
        send(if (off) SetupEvent.AdbOff else SetupEvent.AdbStillOn)
    }

    private suspend fun body() {
        send(SetupEvent.Start(env.wifi(), env.adbWifi(), env.canNotify()))
        if (SetupMachine.terminal(state)) return

        // Fast path: a connect service already exists (paired earlier). Connect first, report after.
        val fast = withTimeoutOrNull(AdbSetup.FAST_PATH.toMillis()) { firstPick(AdbSetup.CONNECT_TYPE) }
        if (fast == null) {
            send(SetupEvent.FastPathMissed)
        } else if (connectWithRetry(fast.port)) {
            send(SetupEvent.ConnectFound(fast.port))
            send(SetupEvent.ConnectOk)
        } else {
            send(SetupEvent.ConnectFailed) // -> Discovering
        }

        while (state is SetupState.Discovering || state is SetupState.AwaitingCode) pairRound()

        if (state is SetupState.Connecting) {
            send(SetupEvent.ConnectFound(firstPick(AdbSetup.CONNECT_TYPE).port))
            if (connectWithRetry((state as SetupState.Connected).port)) send(SetupEvent.ConnectOk)
            else send(SetupEvent.ConnectFailed)
        }

        if (state is SetupState.Provisioning) {
            val out = adb.shell(AdbSetup.DPM_CMD, AdbSetup.SHELL_TIMEOUT).getOrElse { it.message ?: "" }
            send(SetupEvent.DpmOutput(out, env.isOwner()))
        }

        if (state is SetupState.DisablingAdb) disableAdb()
    }

    /** Collects pairing services and codes until paired. A rejected port is stale until NSD reports it lost or a new port appears. */
    private suspend fun pairRound() {
        var stale: Int? = null
        coroutineScope {
            val picker = launch {
                disco.services(AdbSetup.PAIRING_TYPE).collect { list ->
                    if (stale != null && list.none { it.port == stale }) stale = null
                    val pick = ServicePick.pick(list.filter { it.port != stale }, env.localHosts()) ?: return@collect
                    val cur = state
                    if (cur is SetupState.Discovering || (cur is SetupState.AwaitingCode && cur.port != pick.port)) {
                        send(SetupEvent.PairingFound(pick.port))
                    }
                }
            }
            for (raw in codes) {
                send(SetupEvent.CodeEntered(raw))
                val s = state
                if (s is SetupState.Pairing) {
                    val ok = adb.pair(s.port, PairingCode.normalize(raw)!!).isSuccess
                    send(if (ok) SetupEvent.PairOk else SetupEvent.PairFailed)
                    if (state is SetupState.Connecting) break
                    stale = s.port
                    while (codes.tryReceive().isSuccess) { /* drain codes typed for the dead dialog */ }
                }
            }
            picker.cancel()
            if (state is SetupState.Discovering || state is SetupState.AwaitingCode) kotlinx.coroutines.awaitCancellation()
        }
    }
}
