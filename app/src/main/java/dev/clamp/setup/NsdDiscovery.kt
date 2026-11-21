package dev.clamp.setup

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import dev.clamp.core.DiscoveryPort
import dev.clamp.core.MdnsService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// TODO(device): mDNS self-discovery on MIUI/HyperOS; if nothing is found in 5 min the TIMEOUT message points to WITH A COMPUTER.
class NsdDiscovery(ctx: Context) : DiscoveryPort {
    private val nsd = ctx.getSystemService(NsdManager::class.java)

    private sealed interface Ev {
        data class Found(val info: NsdServiceInfo) : Ev
        data class Lost(val name: String) : Ev
    }

    private class Resolved(val info: NsdServiceInfo?, val errorCode: Int = 0)

    @Suppress("DEPRECATION")
    private suspend fun resolveLegacy(info: NsdServiceInfo): Resolved = suspendCancellableCoroutine { cont ->
        // No cancel API before 34: a late callback is ignored and frees the (single) resolve slot.
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) { if (cont.isActive) cont.resume(Resolved(null, errorCode)) }
            override fun onServiceResolved(si: NsdServiceInfo) { if (cont.isActive) cont.resume(Resolved(si)) }
        })
    }

    @androidx.annotation.RequiresApi(34)
    private suspend fun resolveModern(info: NsdServiceInfo): Resolved {
        val holder = arrayOfNulls<NsdManager.ServiceInfoCallback>(1)
        try {
            return suspendCancellableCoroutine { cont ->
                val cb = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) { if (cont.isActive) cont.resume(Resolved(null, errorCode)) }
                    override fun onServiceUpdated(si: NsdServiceInfo) {
                        if (si.hostAddresses.isNotEmpty() && cont.isActive) cont.resume(Resolved(si))
                    }
                    override fun onServiceLost() { if (cont.isActive) cont.resume(Resolved(null, -1)) }
                    override fun onServiceInfoCallbackUnregistered() {}
                }
                holder[0] = cb
                nsd.registerServiceInfoCallback(info, Runnable::run, cb)
            }
        } finally {
            holder[0]?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } } // free the slot on cancel too
        }
    }

    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? {
        var backoff = 200L
        repeat(5) {
            val r = if (Build.VERSION.SDK_INT >= 34) resolveModern(info) else resolveLegacy(info)
            if (r.info != null) return r.info
            if (r.errorCode != NsdManager.FAILURE_ALREADY_ACTIVE) return null
            delay(backoff)
            backoff *= 2
        }
        return null
    }

    private fun hostOf(si: NsdServiceInfo): String? =
        if (Build.VERSION.SDK_INT >= 34) si.hostAddresses.firstOrNull()?.hostAddress
        else @Suppress("DEPRECATION") si.host?.hostAddress

    @Suppress("DEPRECATION")
    override fun services(type: String): Flow<List<MdnsService>> = callbackFlow {
        val events = Channel<Ev>(Channel.UNLIMITED)
        // One coroutine owns the map: no cross-thread mutation.
        launch {
            val map = LinkedHashMap<String, MdnsService>()
            var seq = 0L
            for (ev in events) {
                when (ev) {
                    is Ev.Found -> {
                        val r = resolve(ev.info) ?: continue
                        map[r.serviceName] = MdnsService(r.serviceName, r.port, hostOf(r), seq++)
                    }
                    is Ev.Lost -> map.remove(ev.name)
                }
                trySend(map.values.toList())
            }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(si: NsdServiceInfo) { events.trySend(Ev.Found(si)) }
            override fun onServiceLost(si: NsdServiceInfo) { events.trySend(Ev.Lost(si.serviceName)) }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("mDNS discovery failed ($errorCode)"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            events.close()
        }
    }
}
