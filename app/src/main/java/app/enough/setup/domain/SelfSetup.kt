package app.enough.setup.domain

import java.time.Duration

object AdbSetup {
    const val HOST = "127.0.0.1" // the ONLY socket host in the app
    const val PAIRING_TYPE = "_adb-tls-pairing._tcp"
    const val CONNECT_TYPE = "_adb-tls-connect._tcp"
    /** Component = build applicationId (app.enough / app.enough.debug) + the receiver class. */
    val COMPONENT: String = "${app.enough.BuildConfig.APPLICATION_ID}/app.enough.admin.EnoughAdminReceiver"
    val DPM_CMD: String = "dpm set-device-owner $COMPONENT 2>&1"
    const val ADB_WIFI_OFF = "settings put global adb_wifi_enabled 0"
    val SESSION: Duration = Duration.ofMinutes(5)
    val FAST_PATH: Duration = Duration.ofSeconds(3)
    val CONNECT_RETRY: Duration = Duration.ofSeconds(1)
    const val CONNECT_TRIES = 3
    val SHELL_TIMEOUT: Duration = Duration.ofSeconds(30)
}

data class MdnsService(val name: String, val port: Int, val host: String?, val seq: Long)

object ServicePick {
    private fun isLoopback(h: String) = h.startsWith("127.") || h == "::1"

    /** Names can't identify this phone, so filter by host and use only the port. Newest wins. */
    fun pick(all: List<MdnsService>, localHosts: Set<String>): MdnsService? =
        all.filter { it.port in 1..65535 }
            .mapNotNull { s -> s.host?.substringBefore('%')?.let { h -> s to h } }
            .filter { (_, h) -> h in localHosts || isLoopback(h) }
            .map { it.first }
            .maxByOrNull { it.seq }
}

object PairingCode {
    /** Strip whitespace; exactly 6 ASCII digits, else null. */
    fun normalize(raw: CharSequence?): String? {
        val s = raw?.filter { !it.isWhitespace() }?.toString() ?: return null
        return if (s.length == 6 && s.all { it in '0'..'9' }) s else null
    }
}

enum class SetupFailure(val message: String) {
    WIFI_OFF("Connect to Wi-Fi. Wireless debugging needs it (no internet is used)."),
    ADB_WIFI_OFF("Turn on Wireless debugging in Developer options."),
    NO_NOTIFICATIONS("Allow Enough notifications, or use WITH A COMPUTER."),
    PAIR_REJECTED("Pairing failed. Tap 'Pair device with pairing code' again and retype the code."),
    CONNECT_FAILED("Couldn't reach Wireless debugging. Toggle it off and on, or use WITH A COMPUTER."),
    ACCOUNTS_PRESENT("Remove every account (Mi, Google, others), then tap RETRY."),
    USERS_PRESENT("Delete Second space, guest and other users, then RETRY."),
    ALREADY_OWNER_OTHER("Another app is already device owner. Remove it first."),
    ALREADY_PROVISIONED("This phone refuses new device owners. Use WITH A COMPUTER, or factory reset."),
    NEEDS_SECURITY_SETTINGS("Turn on 'USB debugging (Security settings)' in Developer options, then RETRY."),
    TIMEOUT("Timed out after 5 min. Tap START to try again."),
    UNKNOWN("Setup failed. Use WITH A COMPUTER. Output: "),
}

sealed interface DpmResult {
    data object Success : DpmResult
    data class Failure(val why: SetupFailure, val raw: String) : DpmResult
}

object DpmParser {
    // "Trying to set the device owner (a), but device owner (b) is already set." or "device owner is already set"
    private val OWNER_SET = Regex("device owner\\b[^\\n]*\\bis already set")

    fun parse(out: String): DpmResult {
        val o = out.lowercase()
        fun f(w: SetupFailure) = DpmResult.Failure(w, out)
        return when {
            "success: device owner set" in o -> DpmResult.Success
            "already some accounts" in o -> f(SetupFailure.ACCOUNTS_PRESENT)
            "already several users" in o -> f(SetupFailure.USERS_PRESENT)
            OWNER_SET.containsMatchIn(o) -> f(SetupFailure.ALREADY_OWNER_OTHER)
            "already provisioned" in o -> f(SetupFailure.ALREADY_PROVISIONED)
            // HyperOS 1.0 (Redmi, Android 13) with "USB debugging (Security settings)" off: setActiveAdmin → "Calling identity is not authorized".
            "manage_device_admins" in o || "security settings" in o || "calling identity is not authorized" in o -> f(SetupFailure.NEEDS_SECURITY_SETTINGS)
            else -> f(SetupFailure.UNKNOWN)
        }
    }
}
