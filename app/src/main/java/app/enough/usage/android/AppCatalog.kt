package app.enough.usage.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import app.enough.limits.domain.Config
import app.enough.usage.domain.AppInfo

class AppCatalog(private val ctx: Context) {
    companion object {
        /**
         * Launchers, security/battery managers and dialers per OEM. Suspending one of these can lock the
         * owner out of the home screen, settings or calls, so they are never offered. Names that are not
         * installed are harmless. Package names are not yet checked on real hardware (release-smoke-test.md F9).
         */
        val OEM_CRITICAL: Set<String> = setOf(
            // Xiaomi / Redmi / POCO
            "com.miui.securitycenter", "com.miui.home", "com.mi.android.globallauncher", "com.miui.powerkeeper",
            // Samsung
            "com.sec.android.app.launcher", "com.samsung.android.dialer", "com.samsung.android.incallui", "com.samsung.android.lool",
            // Oppo / Realme / OnePlus
            "com.android.launcher", "net.oneplus.launcher", "com.coloros.safecenter", "com.oppo.safe",
            "com.oplus.battery", "com.coloros.phonemanager", "com.oneplus.security",
            // Vivo / iQOO
            "com.bbk.launcher2", "com.iqoo.secure", "com.vivo.permissionmanager",
            // Huawei / Honor
            "com.huawei.android.launcher", "com.huawei.systemmanager", "com.hihonor.android.launcher", "com.hihonor.systemmanager",
            // Pixel
            "com.google.android.apps.nexuslauncher",
        )
    }

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
     * [OEM_CRITICAL] covers the known OEM launchers and system managers; the rest is resolved at runtime.
     */
    @Suppress("DEPRECATION")
    fun unpickable(config: Config): Set<String> {
        val out = HashSet<String>()
        out += ctx.packageName
        out += "com.android.settings"
        out += OEM_CRITICAL
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
