package dev.clamp.core

sealed interface SetupState {
    data object Idle : SetupState
    data object FastPath : SetupState
    data object Discovering : SetupState
    data class AwaitingCode(val port: Int) : SetupState
    data class Pairing(val port: Int) : SetupState
    data object Connecting : SetupState
    data class Connected(val port: Int) : SetupState
    data object Provisioning : SetupState
    data object DisablingAdb : SetupState
    data object Done : SetupState
    /** dpm succeeded but Wireless debugging could not be turned off: user must do it. */
    data object DoneAdbStillOn : SetupState
    data class Failed(val why: SetupFailure, val raw: String = "") : SetupState
}

sealed interface SetupEvent {
    data class Start(val wifi: Boolean, val adbWifi: Boolean, val canNotify: Boolean) : SetupEvent
    data object FastPathMissed : SetupEvent
    data class PairingFound(val port: Int) : SetupEvent
    data class CodeEntered(val raw: String) : SetupEvent
    data object PairOk : SetupEvent
    data object PairFailed : SetupEvent
    data class ConnectFound(val port: Int) : SetupEvent
    data object ConnectOk : SetupEvent
    data object ConnectFailed : SetupEvent
    data class DpmOutput(val out: String, val isOwnerNow: Boolean) : SetupEvent
    data object AdbOff : SetupEvent
    data object AdbStillOn : SetupEvent
    data class Error(val msg: String) : SetupEvent
    data object Timeout : SetupEvent
    data object Cancel : SetupEvent
}

data class SetupView(val state: SetupState, val log: List<String>)

object SetupMachine {
    const val LOG_CAP = 6

    fun next(s: SetupState, e: SetupEvent): SetupState {
        if (e is SetupEvent.Start) {
            if (s !is SetupState.Idle && s !is SetupState.Failed) return s
            return when {
                !e.canNotify -> SetupState.Failed(SetupFailure.NO_NOTIFICATIONS)
                !e.wifi -> SetupState.Failed(SetupFailure.WIFI_OFF)
                !e.adbWifi -> SetupState.Failed(SetupFailure.ADB_WIFI_OFF)
                else -> SetupState.FastPath
            }
        }
        if (terminal(s)) return s
        if (e is SetupEvent.Error) return SetupState.Failed(SetupFailure.UNKNOWN, e.msg)
        if (e is SetupEvent.Timeout) return SetupState.Failed(SetupFailure.TIMEOUT)
        if (e is SetupEvent.Cancel) return SetupState.Idle
        return when (s) {
            SetupState.FastPath -> when (e) {
                is SetupEvent.ConnectFound -> SetupState.Connected(e.port)
                SetupEvent.FastPathMissed, SetupEvent.ConnectFailed -> SetupState.Discovering
                else -> s
            }
            SetupState.Discovering -> if (e is SetupEvent.PairingFound) SetupState.AwaitingCode(e.port) else s
            is SetupState.AwaitingCode -> when (e) {
                is SetupEvent.CodeEntered ->
                    if (PairingCode.normalize(e.raw) != null) SetupState.Pairing(s.port) else s
                is SetupEvent.PairingFound -> SetupState.AwaitingCode(e.port)
                else -> s
            }
            is SetupState.Pairing -> when (e) {
                SetupEvent.PairOk -> SetupState.Connecting
                SetupEvent.PairFailed -> SetupState.Discovering
                else -> s
            }
            SetupState.Connecting -> when (e) {
                is SetupEvent.ConnectFound -> SetupState.Connected(e.port)
                SetupEvent.ConnectFailed -> SetupState.Failed(SetupFailure.CONNECT_FAILED)
                else -> s
            }
            is SetupState.Connected -> when (e) {
                SetupEvent.ConnectOk -> SetupState.Provisioning
                // Plan gap: a failed connect after ConnectFound must fail (test 25).
                SetupEvent.ConnectFailed -> SetupState.Failed(SetupFailure.CONNECT_FAILED)
                else -> s
            }
            SetupState.Provisioning -> if (e is SetupEvent.DpmOutput) {
                // Already owner: every dpm outcome (incl. unknown output) counts as success.
                if (e.isOwnerNow) SetupState.DisablingAdb
                else when (val r = DpmParser.parse(e.out)) {
                    DpmResult.Success -> SetupState.DisablingAdb
                    is DpmResult.Failure -> SetupState.Failed(r.why, r.raw)
                }
            } else s
            SetupState.DisablingAdb -> when (e) {
                SetupEvent.AdbOff -> SetupState.Done
                SetupEvent.AdbStillOn -> SetupState.DoneAdbStillOn
                else -> s
            }
            else -> s
        }
    }

    fun reduce(v: SetupView, e: SetupEvent): SetupView {
        val n = next(v.state, e)
        val add = ArrayList<String>()
        if (e is SetupEvent.CodeEntered && v.state is SetupState.AwaitingCode && n == v.state) add += "code must be 6 digits"
        if (e is SetupEvent.PairFailed && v.state is SetupState.Pairing) add += SetupFailure.PAIR_REJECTED.message
        if (n != v.state) {
            add += line(n)
            if (n is SetupState.AwaitingCode) add += "waiting for code"
        }
        if (add.isEmpty()) return v.copy(state = n)
        return SetupView(n, (v.log + add).takeLast(LOG_CAP))
    }

    fun line(s: SetupState): String = when (s) {
        SetupState.Idle -> "idle"
        SetupState.FastPath -> "checking existing pairing"
        SetupState.Discovering -> "waiting for pairing dialog · tap 'Pair device with pairing code'"
        is SetupState.AwaitingCode -> "found pairing service · ${AdbSetup.HOST}:${s.port}"
        is SetupState.Pairing -> "pairing…"
        SetupState.Connecting -> "paired · looking for adb"
        is SetupState.Connected -> "connecting · ${AdbSetup.HOST}:${s.port}"
        SetupState.Provisioning -> "running dpm set-device-owner"
        SetupState.DisablingAdb -> "device owner set · turning wireless debugging off"
        SetupState.Done -> "done · activating"
        SetupState.DoneAdbStillOn -> "TURN OFF WIRELESS DEBUGGING MANUALLY"
        is SetupState.Failed -> "failed · " + s.why.message
    }

    fun terminal(s: SetupState) = s is SetupState.Done || s is SetupState.DoneAdbStillOn || s is SetupState.Failed
    /** Single gate for onDeviceOwnerDetected: never while provisioning, nor while adb wifi is still on after setup. */
    fun canActivate(s: SetupState, adbWifiOn: () -> Boolean): Boolean = when (s) {
        SetupState.Provisioning, SetupState.DisablingAdb -> false
        SetupState.DoneAdbStillOn -> !adbWifiOn()
        else -> true
    }

    fun holdsActivation(s: SetupState) = s is SetupState.Provisioning || s is SetupState.DisablingAdb
}
