package app.enough.enforce.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.util.Log
import app.enough.admin.EnoughAdminReceiver
import app.enough.enforce.domain.DevicePort
import app.enough.enforce.domain.SuspendResult

class Policy(private val ctx: Context) : DevicePort {
    private val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
    private val um = ctx.getSystemService(UserManager::class.java)
    private val pm = ctx.packageManager
    val admin = ComponentName(ctx, EnoughAdminReceiver::class.java)
    private val self = ctx.packageName

    override fun isOwner(): Boolean = dpm.isDeviceOwnerApp(self)

    fun isActive(): Boolean = isOwner()

    private fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { Log.w("Enough", "policy step failed: $name", it) }
    }

    private fun check(name: String, block: () -> Boolean): String? =
        if (runCatching(block).getOrDefault(false)) null else name

    private fun restrictions(): List<Pair<String, String>> =
        RESTRICTIONS.map { it to LABELS.getValue(it) }

    /** Applies everything, then verifies. Returns labels of what is missing. Idempotent. */
    // Unverified on HyperOS: each step (release-smoke-test.md section F). AOSP emulator: nothing missing.
    override fun applyProtection(): List<String> {
        step("uninstallBlocked") { dpm.setUninstallBlocked(admin, self, true) }
        step("userControlDisabled") { dpm.setUserControlDisabledPackages(admin, listOf(self)) }
        restrictions().forEach { (r, _) -> step("restriction $r") { dpm.addUserRestriction(admin, r) } }
        // Drop any leftover block from older releases that applied DISALLOW_DEBUGGING_FEATURES.
        step("clear debugging") { dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES) }
        step("autoTime") { dpm.setAutoTimeEnabled(admin, true) }
        step("autoTimeZone") { dpm.setAutoTimeZoneEnabled(admin, true) }
        // Shown by the system "Can't open this app" dialog (Learn more) and "managed by" labels instead of "your IT admin".
        step("orgName") { dpm.setOrganizationName(admin, "Enough") }
        step("supportMsg") { dpm.setShortSupportMessage(admin, "Daily limit reached. Enough unblocks this app at midnight.") }
        step("supportMsgLong") {
            dpm.setLongSupportMessage(
                admin,
                "This phone is locked by Enough, a screen-time app you set up yourself. Blocked apps reopen at midnight. Giving yourself more time, removing a limit or turning Enough off takes 24 hours.",
            )
        }
        if (Build.VERSION.SDK_INT >= 33) {
            step("notifGrant") {
                dpm.setPermissionGrantState(
                    admin, self, "android.permission.POST_NOTIFICATIONS",
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                )
            }
        }
        val missing = ArrayList<String?>()
        missing += check("UNINSTALL BLOCK") { dpm.isUninstallBlocked(admin, self) }
        missing += check("FORCE STOP BLOCK") { self in dpm.getUserControlDisabledPackages(admin) }
        restrictions().forEach { (r, label) -> missing += check(label) { um.hasUserRestriction(r) } }
        missing += check("AUTO TIME") { dpm.getAutoTimeEnabled(admin) }
        missing += check("AUTO TIMEZONE") { dpm.getAutoTimeZoneEnabled(admin) }
        return missing.filterNotNull().also { if (it.isNotEmpty()) Log.w("Enough", "protection missing: $it") }
    }

    // Unverified on MIUI: which packages it refuses (securitycenter, miui.home?) and the "App paused" dialog (release-smoke-test.md section F).
    override fun setSuspended(pkgs: Collection<String>, suspended: Boolean): SuspendResult {
        // Never suspend Enough itself — picker excludes it, this is the hard safety net.
        val filtered = pkgs.filter { it != self }
        if (filtered.isEmpty()) return SuspendResult()
        return try {
            val refused = dpm.setPackagesSuspended(admin, filtered.toTypedArray(), suspended).toList()
            Log.i("Enough", "setPackagesSuspended($suspended) $filtered refused=$refused now=${filtered.map { "$it:" + runCatching { pm.isPackageSuspended(it) }.getOrNull() }}")
            if (refused.isNotEmpty()) Log.w("Enough", "setPackagesSuspended($suspended) refused: $refused")
            SuspendResult(refused)
        } catch (e: Exception) {
            Log.w("Enough", "setPackagesSuspended threw", e)
            SuspendResult(emptyList(), threw = true)
        }
    }

    private fun stillSuspended(pkgs: Collection<String>): List<String> = pkgs.filter {
        try { pm.isPackageSuspended(it) } catch (e: PackageManager.NameNotFoundException) { false } catch (e: Exception) { true }
    }

    /**
     * Success only if nothing is still suspended and device owner is cleared.
     * clearDeviceOwnerApp is never called while anything is suspended.
     */
    override fun release(pkgs: Collection<String>): Boolean {
        var todo = pkgs.toList()
        repeat(3) {
            if (todo.isNotEmpty() && isOwner()) setSuspended(todo, false)
            todo = stillSuspended(todo)
        }
        if (todo.isNotEmpty()) {
            Log.w("Enough", "release: still suspended $todo")
            return false
        }
        if (isOwner()) {
            (RESTRICTIONS + UserManager.DISALLOW_DEBUGGING_FEATURES).forEach { r ->
                step("clear $r") { dpm.clearUserRestriction(admin, r) }
            }
            step("userControl") { dpm.setUserControlDisabledPackages(admin, emptyList()) }
            step("uninstall") { dpm.setUninstallBlocked(admin, self, false) }
            @Suppress("DEPRECATION")
            step("clearOwner") { dpm.clearDeviceOwnerApp(self) }
        }
        return !isOwner()
    }

    companion object {
        val RESTRICTIONS = listOf(
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_CONFIG_DATE_TIME,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_USER_SWITCH,
            UserManager.DISALLOW_ADD_MANAGED_PROFILE,
        )
        private val LABELS = mapOf(
            UserManager.DISALLOW_SAFE_BOOT to "SAFE BOOT",
            UserManager.DISALLOW_CONFIG_DATE_TIME to "DATE/TIME",
            UserManager.DISALLOW_ADD_USER to "ADD USER",
            UserManager.DISALLOW_USER_SWITCH to "USER SWITCH",
            UserManager.DISALLOW_ADD_MANAGED_PROFILE to "MANAGED PROFILE",
        )
    }
}
