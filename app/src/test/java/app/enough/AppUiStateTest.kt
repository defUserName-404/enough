package app.enough

import app.enough.limits.domain.Config
import app.enough.limits.domain.Phase
import app.enough.limits.domain.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUiStateTest {
    private val setup = Config(phase = Phase.SETUP)
    private val active = Config(phase = Phase.ACTIVE, targets = listOf(Target("a", 20)))
    private val edit = EditTarget(Target("a", 20), isNew = false)

    private fun ready(s: AppUiState) = s as AppUiState.Ready

    @Test fun loading_failed_releasing() {
        assertEquals(AppUiState.Loading, AppViewModel.toUiState(null, false, null, false, null, null))
        assertEquals(AppUiState.LoadFailed, AppViewModel.toUiState(active, true, null, false, null, null))
        assertEquals(
            AppUiState.Releasing(true),
            AppViewModel.toUiState(active.copy(phase = Phase.RELEASING, releaseFailed = true), false, null, false, null, null),
        )
    }

    @Test fun setup_screens() {
        assertEquals(Screen.APPS, ready(AppViewModel.toUiState(setup, false, null, false, null, null)).screen)
        assertEquals(Screen.LOCK, ready(AppViewModel.toUiState(setup.copy(targets = active.targets), false, null, false, null, null)).screen)
        assertEquals(Screen.APPS, ready(AppViewModel.toUiState(setup.copy(targets = active.targets), false, Screen.APPS, false, null, null)).screen)
    }

    @Test fun active_screens_and_editor_only_over_home_area() {
        assertEquals(Screen.HOME, ready(AppViewModel.toUiState(active, false, Screen.LOCK, false, null, edit)).screen)
        assertEquals(Screen.USAGE, ready(AppViewModel.toUiState(active, false, null, false, Screen.USAGE, edit)).screen)
        val adding = ready(AppViewModel.toUiState(active, false, null, true, Screen.SETTINGS, edit))
        assertEquals(Screen.ADD, adding.screen)
        assertNull(adding.editing)
        assertEquals(edit, ready(AppViewModel.toUiState(active, false, null, false, null, edit)).editing)
    }
}
