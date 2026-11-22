package app.enough.limits.ui

import app.enough.EditTarget
import app.enough.limits.domain.Change
import app.enough.limits.domain.Config
import app.enough.limits.domain.PendingChange
import app.enough.limits.domain.Target
import app.enough.testutil.NOW
import app.enough.usage.android.LiveUsage
import app.enough.usage.domain.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenStateTest {
    private val apps = listOf(AppInfo("a", "A"), AppInfo("b", "B"))
    private val cfg = Config(targets = listOf(Target("a", 20)))

    @Test fun picker_setup_keeps_targets_and_all_apps() {
        val s = PickerViewModel.uiState(PickerMode.SETUP, cfg, apps)
        assertEquals(cfg.targets, s.initial)
        assertEquals(apps, s.apps)
    }

    @Test fun picker_add_hides_limited_apps() {
        val s = PickerViewModel.uiState(PickerMode.ADD, cfg, apps)
        assertEquals(emptyList<Target>(), s.initial)
        assertEquals(listOf(AppInfo("b", "B")), s.apps)
    }

    @Test fun edit_sheet_uses_saved_target_label_and_pending() {
        val pending = PendingChange("1", Change.RaiseLimit(Target("a", 40)), NOW)
        val c = cfg.copy(pending = listOf(pending))
        val live = LiveUsage(now = NOW, labels = mapOf("a" to "App A"))
        val s = EditLimitViewModel.build(EditTarget(Target("a", 30), isNew = false), c, live)!!
        assertEquals(Target("a", 20), s.target) // saved limit wins over the requested one
        assertEquals("App A", s.label)
        assertEquals(pending, s.pending)
        assertEquals(NOW, s.now)
    }

    @Test fun edit_sheet_new_app_falls_back_to_pkg() {
        val s = EditLimitViewModel.build(EditTarget(Target("z", 30), isNew = true), cfg, LiveUsage(now = NOW))!!
        assertEquals(Target("z", 30), s.target)
        assertEquals("z", s.label)
        assertNull(s.pending)
        assertNull(EditLimitViewModel.build(null, cfg, LiveUsage()))
    }
}
