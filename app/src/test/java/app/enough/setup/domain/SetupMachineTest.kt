package app.enough.setup.domain

import app.enough.setup.domain.SetupEvent.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupMachineTest {
    private fun run(vararg e: SetupEvent, from: SetupState = SetupState.Idle): SetupState =
        e.fold(from) { s, ev -> SetupMachine.next(s, ev) }

    @Test fun preconditions() {
        assertEquals(SetupState.Failed(SetupFailure.WIFI_OFF), run(Start(false, true, true)))
        assertEquals(SetupState.Failed(SetupFailure.ADB_WIFI_OFF), run(Start(true, false, true)))
        assertEquals(SetupState.Failed(SetupFailure.NO_NOTIFICATIONS), run(Start(true, true, false)))
    }

    @Test fun happy_path() {
        var s: SetupState = SetupState.Idle
        fun step(e: SetupEvent, expect: SetupState) { s = SetupMachine.next(s, e); assertEquals(expect, s) }
        step(Start(true, true, true), SetupState.FastPath)
        step(FastPathMissed, SetupState.Discovering)
        step(PairingFound(37145), SetupState.AwaitingCode(37145))
        step(CodeEntered("482915"), SetupState.Pairing(37145))
        step(PairOk, SetupState.Connecting)
        step(ConnectFound(41877), SetupState.Connected(41877))
        step(ConnectOk, SetupState.Provisioning)
        step(DpmOutput("Success: Device owner set to package x", false), SetupState.DisablingAdb)
        step(AdbOff, SetupState.Done)
    }

    @Test fun short_code_logs() {
        val v = SetupView(SetupState.AwaitingCode(1), emptyList())
        val r = SetupMachine.reduce(v, CodeEntered("48291"))
        assertEquals(v.state, r.state)
        assertTrue("code must be 6 digits" in r.log)
    }

    @Test fun pair_failed_retry() {
        assertEquals(SetupState.Discovering, run(PairFailed, from = SetupState.Pairing(1)))
        assertEquals(SetupState.AwaitingCode(9), run(PairingFound(9), from = SetupState.Discovering))
        assertEquals(SetupState.AwaitingCode(9), run(PairingFound(9), from = SetupState.AwaitingCode(1)))
    }

    @Test fun timeout() {
        assertEquals(SetupState.Failed(SetupFailure.TIMEOUT), run(Timeout, from = SetupState.AwaitingCode(1)))
        assertEquals(SetupState.Done, run(Timeout, from = SetupState.Done))
    }

    @Test fun fast_path() {
        assertEquals(SetupState.Connected(5), run(ConnectFound(5), from = SetupState.FastPath))
    }

    @Test fun already_owner() {
        val t = "Trying to set the device owner (a), but device owner (b) is already set."
        assertEquals(SetupState.DisablingAdb, run(DpmOutput(t, true), from = SetupState.Provisioning))
        assertEquals(
            SetupState.Failed(SetupFailure.ALREADY_OWNER_OTHER, t),
            run(DpmOutput(t, false), from = SetupState.Provisioning),
        )
    }

    @Test fun holds_activation_log_cap_and_line() {
        assertTrue(SetupMachine.holdsActivation(SetupState.Provisioning))
        assertTrue(SetupMachine.holdsActivation(SetupState.DisablingAdb))
        assertFalse(SetupMachine.holdsActivation(SetupState.Connecting))
        assertFalse(SetupMachine.holdsActivation(SetupState.Done))
        var v = SetupView(SetupState.Idle, emptyList())
        for (i in 1..10) v = SetupMachine.reduce(v, PairingFound(i)).let { SetupMachine.reduce(it, Cancel) }
        v = SetupMachine.reduce(SetupView(SetupState.Discovering, v.log), PairingFound(1))
        assertTrue(v.log.size <= 6)
        assertEquals("found pairing service · 127.0.0.1:37145", SetupMachine.line(SetupState.AwaitingCode(37145)))
    }

    @Test fun adb_still_on_and_error_and_owner_any_failure() {
        assertEquals(SetupState.DoneAdbStillOn, run(AdbStillOn, from = SetupState.DisablingAdb))
        assertEquals(SetupState.Failed(SetupFailure.UNKNOWN, "x"), run(Error("x"), from = SetupState.Discovering))
        assertEquals(SetupState.DisablingAdb, run(DpmOutput("garbage", true), from = SetupState.Provisioning))
        assertEquals(SetupState.DisablingAdb, run(DpmOutput("Success: Device owner set", true), from = SetupState.Provisioning))
        assertEquals("TURN OFF WIRELESS DEBUGGING MANUALLY", SetupMachine.line(SetupState.DoneAdbStillOn))
        assertTrue(SetupMachine.terminal(SetupState.DoneAdbStillOn))
    }

    @Test fun can_activate_gate() {
        assertFalse(SetupMachine.canActivate(SetupState.Provisioning) { false })
        assertFalse(SetupMachine.canActivate(SetupState.DisablingAdb) { false })
        assertFalse(SetupMachine.canActivate(SetupState.DoneAdbStillOn) { true })
        assertTrue(SetupMachine.canActivate(SetupState.DoneAdbStillOn) { false })
        assertTrue(SetupMachine.canActivate(SetupState.Done) { true })
        assertTrue(SetupMachine.canActivate(SetupState.Idle) { true })
    }

    @Test fun second_start_is_ignored() {
        assertEquals(SetupState.FastPath, run(Start(true, true, true), from = SetupState.FastPath))
        assertEquals(SetupState.Provisioning, run(Start(true, true, true), from = SetupState.Provisioning))
    }
}
