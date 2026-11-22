package app.enough.setup.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.enough.Graph
import app.enough.oem.android.OemItem
import app.enough.oem.domain.OemSpec
import app.enough.setup.domain.SetupView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SetupUiState(
    val usageGranted: Boolean,
    val oemItems: List<OemSpec>,
    val oemDone: Set<String>,
    val phone: PhoneSetupUi,
)

/** Setup step 2 (lock): permissions, OEM battery/autostart items, wireless-debugging pairing. */
class SetupViewModel(
    private val graph: Graph,
    resumeTick: StateFlow<Int>,
    notifDenied: StateFlow<Boolean>,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val items: List<OemItem> = graph.oem.items()
    private val oemOpened = saved.getStateFlow(K_OEM_DONE, emptyList<String>())
    private val accountsConfirmed = saved.getStateFlow(K_ACCOUNTS, false)

    /** Re-read on every resume (the user comes back from a settings screen). */
    private data class Probe(val usageGranted: Boolean, val wifi: Boolean, val adbWifi: Boolean, val batteryUnrestricted: Boolean)

    private fun probe() = Probe(
        usageGranted = graph.usage.hasAccess(),
        wifi = graph.setupEnv.wifi(),
        adbWifi = graph.setupEnv.adbWifi(),
        batteryUnrestricted = graph.oem.batteryUnrestricted(),
    )

    private val probes = combine(resumeTick, oemOpened) { _, _ -> probe() }

    val uiState: StateFlow<SetupUiState> =
        combine(probes, oemOpened, accountsConfirmed, graph.setup.view, notifDenied) { p, opened, accounts, view, denied ->
            build(p, opened, accounts, view, denied)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(1_000),
            build(probe(), oemOpened.value, accountsConfirmed.value, graph.setup.view.value, notifDenied.value),
        )

    private fun build(p: Probe, opened: List<String>, accounts: Boolean, view: SetupView, denied: Boolean) =
        SetupUiState(
            usageGranted = p.usageGranted,
            oemItems = items.map { it.spec },
            oemDone = opened.toSet() + items.filter { it.spec.id == "battery" && p.batteryUnrestricted }.map { it.spec.id },
            phone = PhoneSetupUi(p.wifi, p.adbWifi, accounts, view, denied),
        )

    fun toggleAccounts() { saved[K_ACCOUNTS] = !accountsConfirmed.value }
    fun cancelSetup() = graph.setup.cancel()

    /** Marks the item done and returns what the Activity should launch. */
    fun openOem(spec: OemSpec): OemItem? {
        saved[K_OEM_DONE] = oemOpened.value + spec.id
        return items.firstOrNull { it.spec.id == spec.id }
    }

    /**
     * Runs while the lock screen is visible with usage access granted (otherwise activation
     * would instantly fail closed). The setup gate guarantees adb wifi goes off before
     * activation applies the other restrictions.
     */
    suspend fun awaitActivation() {
        while (true) {
            if (graph.policy.isActive() && graph.setup.canActivate()) {
                graph.controller.onDeviceOwnerDetected()
                return
            }
            delay(2_000)
        }
    }

    private companion object {
        const val K_OEM_DONE = "oemDone"
        const val K_ACCOUNTS = "accountsConfirmed"
    }
}

data class PhoneSetupUi(
    val wifi: Boolean,
    val adbWifi: Boolean,
    val accountsConfirmed: Boolean,
    val view: SetupView,
    val notifDenied: Boolean = false,
)
