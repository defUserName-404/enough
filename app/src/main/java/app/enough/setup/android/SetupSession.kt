package app.enough.setup.android

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import app.enough.setup.domain.SetupMachine
import app.enough.setup.domain.SetupState
import app.enough.setup.domain.SetupView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

/** Process-wide holder. Never touches DataStore. */
class SetupSession(private val app: Application) {
    val view = MutableStateFlow(SetupView(SetupState.Idle, emptyList()))
    val codes = Channel<String>(Channel.CONFLATED)

    fun submitCode(raw: String) {
        codes.trySend(raw)
    }

    private val env by lazy { SetupEnv(app) }

    /** The one gate for onDeviceOwnerDetected, used by MainActivity and the lock screen. */
    fun canActivate(): Boolean = SetupMachine.canActivate(view.value.state) { env.adbWifi() }

    fun start() {
        ContextCompat.startForegroundService(app, Intent(app, SetupService::class.java))
    }

    fun cancel() {
        app.startService(Intent(app, SetupService::class.java).setAction(SetupService.ACTION_CANCEL))
    }
}
