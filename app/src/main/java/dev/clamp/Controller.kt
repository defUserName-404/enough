package dev.clamp

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.clamp.admin.Policy
import dev.clamp.core.Change
import dev.clamp.core.ChangePolicy
import dev.clamp.core.Kind
import dev.clamp.core.Limits
import dev.clamp.core.Outcome
import dev.clamp.core.Phase
import dev.clamp.core.Releaser
import dev.clamp.core.Target
import dev.clamp.data.ConfigStore
import dev.clamp.service.EnforcementService
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** UI-facing facade. Every mutation goes through store.update. */
class Controller(
    private val ctx: Context,
    private val store: ConfigStore,
    private val policy: Policy,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    val releaser = Releaser(store, policy)

    suspend fun saveSetupTargets(targets: List<Target>) {
        store.update {
            if (it.phase == Phase.SETUP) it.copy(targets = targets.map { t -> t.copy(limitMin = Limits.clamp(t.limitMin)) }) else it
        }
    }

    suspend fun onDeviceOwnerDetected() {
        if (store.get().phase != Phase.SETUP) return
        val missing = withContext(Dispatchers.Default) { policy.applyProtection() }
        store.update {
            if (it.phase == Phase.SETUP) it.copy(phase = Phase.ACTIVE, protectionMissing = missing) else it
        }
        startService()
    }

    suspend fun setLimit(pkg: String, minutes: Int): Kind {
        val change = ChangePolicy(clock()).limitChange(store.get(), pkg, minutes) ?: return Kind.NOOP
        return submit(change)
    }

    private fun sanitize(c: Change): Change = when (c) {
        is Change.AddOrLower -> Change.AddOrLower(c.target.copy(limitMin = Limits.clamp(c.target.limitMin)))
        is Change.RaiseLimit -> Change.RaiseLimit(c.target.copy(limitMin = Limits.clamp(c.target.limitMin)))
        else -> c
    }

    /** Only in ACTIVE; a no-op otherwise (e.g. RELEASING). */
    suspend fun submit(change: Change): Kind {
        var out: Outcome? = null
        store.update { cfg ->
            if (cfg.phase != Phase.ACTIVE) {
                out = Outcome(cfg, Kind.NOOP, emptyList())
                cfg
            } else {
                val o = ChangePolicy(clock()).submit(cfg, sanitize(change))
                out = o
                o.config
            }
        }
        val o = out!!
        if (o.kind != Kind.NOOP) kickService()
        return o.kind
    }

    suspend fun cancel(id: String) {
        store.update { ChangePolicy(clock()).cancel(it, id) }
    }

    /** Idempotent and serialized. Returns true when fully released. */
    suspend fun runRelease(): Boolean {
        val ok = withContext(Dispatchers.Default) { releaser.attempt() }
        if (ok) finishRelease()
        return ok
    }

    fun finishRelease() {
        ctx.stopService(Intent(ctx, EnforcementService::class.java))
        NotificationManagerCompat.from(ctx).cancelAll()
    }

    fun startService() {
        ContextCompat.startForegroundService(ctx, Intent(ctx, EnforcementService::class.java))
    }

    fun kickService() {
        ContextCompat.startForegroundService(
            ctx, Intent(ctx, EnforcementService::class.java).apply { action = EnforcementService.ACTION_TICK },
        )
    }
}
