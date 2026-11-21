package dev.clamp.setup

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dev.clamp.ClampApp
import dev.clamp.core.SetupFailure
import dev.clamp.core.SetupRunner
import dev.clamp.core.SetupState
import dev.clamp.core.SetupView
import dev.clamp.service.Notifier
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Short-lived foreground service; the 5-minute cap lives in SetupRunner. */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupService : Service() {
    companion object {
        const val ACTION_CANCEL = "dev.clamp.SETUP_CANCEL"
    }

    // Single-threaded: the runner's state is not synchronized.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var job: Job? = null
    private val graph get() = (applicationContext as ClampApp).graph

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val old = job
            scope.launch {
                old?.cancelAndJoin() // runner's finally closes the connection before we reset
                graph.setup.view.value = SetupView(SetupState.Idle, emptyList())
                NotificationManagerCompat.from(this@SetupService).cancel(Notifier.ID_SETUP)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        ServiceCompat.startForeground(
            this, Notifier.ID_SETUP, graph.notifier.setup(SetupView(SetupState.Idle, emptyList())),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        val session = graph.setup
        while (session.codes.tryReceive().isSuccess) { /* drop stale codes */ }
        job = scope.launch {
            val runner = SetupRunner(
                AdbClient(this@SetupService), NsdDiscovery(this@SetupService), SetupEnv(this@SetupService), session.codes,
            ) { v -> if (isActive) publish(v) }
            try {
                runner.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publish(SetupView(SetupState.Failed(SetupFailure.UNKNOWN, e.message ?: ""), session.view.value.log))
            }
            finish()
        }
        return START_NOT_STICKY
    }

    private fun publish(v: SetupView) {
        graph.setup.view.value = v
        graph.notifier.postSetup(v)
    }

    private fun finish() {
        val done = graph.setup.view.value.state is SetupState.Done
        stopForeground(STOP_FOREGROUND_DETACH)
        if (done) NotificationManagerCompat.from(this).cancel(Notifier.ID_SETUP)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
