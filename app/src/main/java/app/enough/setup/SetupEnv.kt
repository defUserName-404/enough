package app.enough.setup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import app.enough.EnoughApp
import app.enough.core.SetupEnvPort
import java.net.NetworkInterface

class SetupEnv(private val ctx: Context) : SetupEnvPort {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)

    override fun wifi(): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

    override fun adbWifi(): Boolean = Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) == 1

    /** Only enumerates addresses; opens no socket. */
    override fun localHosts(): Set<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
            .toSet()
    }.getOrDefault(emptySet())

    override fun canNotify(): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    override fun isOwner(): Boolean = (ctx.applicationContext as EnoughApp).graph.policy.isOwner()
}
