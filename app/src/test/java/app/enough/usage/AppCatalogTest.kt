package app.enough.usage

import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogTest {
    @Test fun oem_critical_keeps_the_miui_packages() {
        assertTrue(AppCatalog.OEM_CRITICAL.containsAll(setOf("com.miui.securitycenter", "com.miui.home")))
    }

    @Test fun oem_critical_covers_each_major_oem_launcher() {
        val launchers = setOf(
            "com.sec.android.app.launcher", // Samsung
            "net.oneplus.launcher", // OnePlus
            "com.bbk.launcher2", // Vivo
            "com.huawei.android.launcher", // Huawei
            "com.google.android.apps.nexuslauncher", // Pixel
        )
        assertTrue(AppCatalog.OEM_CRITICAL.containsAll(launchers))
    }

    @Test fun oem_critical_never_blocks_enough_itself_by_accident() {
        assertTrue(AppCatalog.OEM_CRITICAL.none { it.startsWith("app.enough") })
    }
}
