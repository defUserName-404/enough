package app.enough.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.UserManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.enough.EnoughApp
import app.enough.core.TickResult
import app.enough.core.Ticker
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

// TODO(device): whether MIUI kills a specialUse FGS despite autostart; whether LOCKED_BOOT_COMPLETED arrives on MIUI.
class EnforcementService : Service() {
    companion object {
        const val ACTION_TICK = "app.enough.TICK"
        const val NOTIF_ID = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null
    private val graph get() = (applicationContext as EnoughApp).graph

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) wake.trySend(Unit)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, screenRx,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_USER_UNLOCKED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must happen within 5 s, even in direct boot. Nothing here may touch DataStore.
        ServiceCompat.startForeground(
            this, NOTIF_ID, graph.notifier.ongoing(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        wake.trySend(Unit)
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
        return START_STICKY
    }

    private suspend fun CoroutineScope.runLoop() {
        val um = getSystemService(UserManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        var ticker: Ticker? = null
        while (isActive) {
            if (!um.isUserUnlocked || !pm.isInteractive) { // NFR-3
                wake.receive()
                continue
            }
            val t = ticker ?: Ticker(graph.store, graph.usage, graph.policy, graph.notifier, graph.controller.releaser) { Clock.systemDefaultZone() }
                .also { ticker = it }
            val r = try {
                t.tick()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Enough", "tick failed", e)
                TickResult.Next(Ticker.SLOW)
            }
            Log.i("Enough", "tick -> $r")
            when (r) {
                is TickResult.Next -> withTimeoutOrNull(r.delay.toMillis()) { wake.receive() }
                TickResult.Release -> {
                    graph.controller.finishRelease()
                    stopSelf()
                    break
                }
                TickResult.Stop, TickResult.NotOwner -> {
                    stopSelf()
                    break
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenRx)
        scope.cancel()
        super.onDestroy()
    }
}
