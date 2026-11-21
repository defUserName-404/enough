package dev.clamp.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import dev.clamp.core.AppInfo
import dev.clamp.core.Config

class AppCatalog(private val ctx: Context) {
    private val pm = ctx.packageManager

    @Suppress("DEPRECATION")
    fun launchable(): List<AppInfo> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .distinctBy { it.activityInfo.packageName }
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
    }

    /**
     * Packages that must not be picked. No public API probes suspendability, so [Config.unsuspendable]
     * (packages DPM refused at runtime) is the only dynamic signal.
     * TODO(device): other OEM launchers / system UIs beyond the MIUI ones below.
     */
    @Suppress("DEPRECATION")
    fun unpickable(config: Config): Set<String> {
        val out = HashSet<String>()
        out += ctx.packageName
        out += "com.android.settings"
        out += "com.miui.securitycenter"
        out += "com.miui.home"
        pm.resolveActivity(Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let { out += it }
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let { out += it }
        ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { out += it }
        out += config.unsuspendable
        return out
    }

    /** Packages that count toward whole-phone screen time: launchable apps minus self, systemui and launchers. */
    @Suppress("DEPRECATION")
    fun counted(): Set<String> {
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
        return launchable().map { it.pkg }.toSet() - ctx.packageName - "com.android.systemui" - homes
    }

    private val labels = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Cached; the first call per package is a binder call, so call off the main thread. */
    fun label(pkg: String): String = labels.getOrPut(pkg) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }
}
