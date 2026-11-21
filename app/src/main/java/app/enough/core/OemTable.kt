package app.enough.core

data class OemComponent(val pkg: String, val cls: String)

data class OemSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val components: List<OemComponent>,
    val bestEffort: Boolean,
)

object OemTable {
    // Unverified on MIUI/HyperOS: bestEffort component names, HyperOS autostart path, MIUI battery extras (release-smoke-test.md section F).
    fun specsFor(manufacturer: String, brand: String): List<OemSpec> {
        val keys = setOf(manufacturer.lowercase(), brand.lowercase())
        fun any(vararg n: String) = n.any { it in keys }
        return when {
            any("xiaomi", "redmi", "poco") -> listOf(
                OemSpec(
                    "autostart", "AUTOSTART", "MIUI · REQUIRED",
                    listOf(OemComponent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                    false,
                ),
                OemSpec(
                    "battery", "BATTERY: NO RESTRICTIONS", "MIUI · REQUIRED",
                    listOf(OemComponent("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")),
                    true,
                ),
            )
            any("oppo", "realme", "oneplus") -> listOf(
                OemSpec(
                    "startup", "STARTUP MANAGER", "OEM · RECOMMENDED",
                    listOf(
                        OemComponent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                        OemComponent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                        OemComponent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                        OemComponent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                    ),
                    true,
                ),
            )
            any("vivo", "iqoo") -> listOf(
                OemSpec(
                    "bgpower", "BACKGROUND POWER", "OEM · RECOMMENDED",
                    listOf(
                        OemComponent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                        OemComponent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                    ),
                    true,
                ),
            )
            any("huawei", "honor") -> listOf(
                OemSpec(
                    "applaunch", "APP LAUNCH", "OEM · RECOMMENDED",
                    listOf(
                        OemComponent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                        OemComponent("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                        OemComponent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                    ),
                    true,
                ),
            )
            any("samsung") -> listOf(
                OemSpec(
                    "neversleep", "NEVER SLEEPING APPS", "OEM · RECOMMENDED",
                    listOf(OemComponent("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                    true,
                ),
            )
            else -> emptyList()
        }
    }
}
