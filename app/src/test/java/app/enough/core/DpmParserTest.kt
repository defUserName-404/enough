package app.enough.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DpmParserTest {
    private fun why(s: String) = (DpmParser.parse(s) as DpmResult.Failure).why

    @Test fun success() {
        val o = "Success: Device owner set to package ComponentInfo{app.enough/app.enough.admin.EnoughAdminReceiver}\nActive admin set to component {app.enough/x}"
        assertEquals(DpmResult.Success, DpmParser.parse(o))
    }

    @Test fun accounts_users() {
        assertEquals(SetupFailure.ACCOUNTS_PRESENT, why("java.lang.IllegalStateException: Not allowed to set the device owner because there are already some accounts on the device"))
        assertEquals(SetupFailure.USERS_PRESENT, why("Not allowed to set the device owner because there are already several users on the device"))
    }

    @Test fun narrowed_patterns() {
        assertEquals(SetupFailure.ALREADY_OWNER_OTHER, why("device owner is already set"))
        assertEquals(SetupFailure.UNKNOWN, why("Something is already set"))
        assertEquals(SetupFailure.UNKNOWN, why("java.lang.SecurityException: Permission Denial: something else"))
        assertEquals(SetupFailure.NEEDS_SECURITY_SETTINGS, why("enable USB debugging (Security settings)"))
    }

    @Test fun owner_and_provisioned() {
        assertEquals(SetupFailure.ALREADY_OWNER_OTHER, why("Trying to set the device owner (a), but device owner (b) is already set."))
        assertEquals(SetupFailure.ALREADY_PROVISIONED, why("Not allowed to set the device owner because the device is already provisioned"))
    }

    @Test fun security_and_unknown() {
        assertEquals(SetupFailure.NEEDS_SECURITY_SETTINGS, why("java.lang.SecurityException: Neither user 2000 nor current process has android.permission.MANAGE_DEVICE_ADMINS"))
        // Real output captured on HyperOS 1.0 / Android 13 (Redmi 2201116TI)
        assertEquals(SetupFailure.NEEDS_SECURITY_SETTINGS, why("Exception occurred while executing 'set-device-owner':\njava.lang.SecurityException: Calling identity is not authorized\n\tat com.android.internal.util.Preconditions.checkCallAuthorization(Preconditions.java:247)"))
        assertEquals(SetupFailure.UNKNOWN, why(""))
        val r = DpmParser.parse("Error: Unknown admin") as DpmResult.Failure
        assertEquals(SetupFailure.UNKNOWN, r.why)
        assertEquals("Error: Unknown admin", r.raw)
    }
}
