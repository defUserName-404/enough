package app.enough.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import app.enough.core.SetupMachine
import app.enough.core.SetupState
import app.enough.core.SetupView
import app.enough.setup.PairCodeReceiver
import app.enough.MainActivity
import app.enough.R
import app.enough.core.NotifyPort
import app.enough.usage.AppCatalog
import java.time.Duration

// TODO(device): whether the POST_NOTIFICATIONS self-grant works on MIUI
class Notifier(private val ctx: Context, private val catalog: AppCatalog) : NotifyPort {
    companion object {
        const val CH_SERVICE = "enforcement"
        const val CH_ALERTS = "alerts"
        const val ID_FAIL = 2
        const val ID_OWNER = 3
        const val ID_CONFIG = 4
        const val CH_SETUP = "setup"
        const val ID_SETUP = 5
        const val ID_RELEASED = 6
        const val KEY_CODE = "pair_code"

        fun ensureChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Enforcement", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Warnings", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CH_SETUP, "Setup", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    fun ongoing(): Notification = NotificationCompat.Builder(ctx, CH_SERVICE)
        .setSmallIcon(R.drawable.ic_enough)
        .setContentTitle("ENOUGH ACTIVE")
        .setOngoing(true)
        .setContentIntent(openApp())
        .build()

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // Guarded by canPost(); lint can't see through the helper.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, n: Notification) {
        if (canPost()) NotificationManagerCompat.from(ctx).notify(id, n)
    }

    override fun warn(pkg: String, left: Duration) {
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_enough)
            .setContentTitle("${catalog.label(pkg).uppercase()} · 2 MIN LEFT")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        post(1000 + (pkg.hashCode() and 0xFFFF), n)
    }

    override fun failClosed(show: Boolean) {
        if (!show) {
            NotificationManagerCompat.from(ctx).cancel(ID_FAIL)
            return
        }
        val pi = PendingIntent.getActivity(
            ctx, 1, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_enough)
            .setContentTitle("USAGE ACCESS REVOKED · ALL APPS BLOCKED")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        post(ID_FAIL, n)
    }

    override fun configError() {
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_enough)
            .setContentTitle("ENOUGH CONFIG UNREADABLE")
            .setContentIntent(openApp())
            .build()
        post(ID_CONFIG, n)
    }

    fun released() {
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_enough)
            .setContentTitle("ENOUGH IS OFF · YOU CAN UNINSTALL IT NOW")
            .setContentText("Long-press the app icon, then Uninstall.")
            .setAutoCancel(true)
            .build()
        post(ID_RELEASED, n)
    }

    override fun lostOwner() {
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_enough)
            .setContentTitle("ENOUGH LOST DEVICE OWNER · NOT ENFORCING")
            .setContentIntent(openApp())
            .build()
        post(ID_OWNER, n)
    }

    fun setup(v: SetupView): Notification {
        val awaiting = v.state is SetupState.AwaitingCode
        val b = NotificationCompat.Builder(ctx, CH_SETUP)
            .setSmallIcon(R.drawable.ic_enough)
            .setContentTitle(if (awaiting) "ENTER THE PAIRING CODE" else "ENOUGH · SETUP")
            .setContentText(v.log.lastOrNull() ?: "starting")
            .setOnlyAlertOnce(true)
            .setOngoing(!SetupMachine.terminal(v.state))
            .setAutoCancel(SetupMachine.terminal(v.state))
            .setContentIntent(openApp())
        if (awaiting) {
            val ri = RemoteInput.Builder(KEY_CODE).setLabel("Enter pairing code").build()
            // RemoteInput requires a MUTABLE PendingIntent; the intent is explicit (our own receiver).
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, PairCodeReceiver::class.java), flags)
            b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_enough, "PAIR", pi).addRemoteInput(ri).setAllowGeneratedReplies(false).build())
        }
        return b.build()
    }

    fun postSetup(v: SetupView) = post(ID_SETUP, setup(v))
}
