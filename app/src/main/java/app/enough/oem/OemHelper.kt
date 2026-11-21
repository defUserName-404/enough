package app.enough.oem

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import app.enough.core.OemSpec
import app.enough.core.OemTable

data class OemItem(val spec: OemSpec, val intent: Intent)

class OemHelper(private val ctx: Context) {
    private val pm = ctx.packageManager

    fun items(): List<OemItem> =
        OemTable.specsFor(Build.MANUFACTURER, Build.BRAND).map { OemItem(it, resolve(it)) }

    private fun fallback() =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Suppress("DEPRECATION")
    private fun resolve(spec: OemSpec): Intent {
        for (c in spec.components) {
            val i = Intent().setComponent(ComponentName(c.pkg, c.cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (spec.id == "battery") {
                i.putExtra("package_name", ctx.packageName)
                i.putExtra("package_label", ctx.applicationInfo.loadLabel(pm).toString())
            }
            if (pm.resolveActivity(i, 0) != null) return i
        }
        return fallback()
    }

    fun launch(activity: Activity, item: OemItem) {
        try {
            activity.startActivity(item.intent)
        } catch (e: ActivityNotFoundException) {
            activity.startActivity(fallback())
        } catch (e: SecurityException) {
            // MIUI throws this on some security-center activities.
            activity.startActivity(fallback())
        }
    }

    // TODO(device): MIUI battery saver state is separate from the Doze whitelist
    fun batteryUnrestricted(): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
}
