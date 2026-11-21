package app.enough.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import app.enough.core.UsageEvt
import app.enough.core.UsageFolder
import app.enough.core.UsagePort
import app.enough.core.UsageSnapshot
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

// TODO(device): event latency; whether MIUI emits ACTIVITY_PAUSED reliably for floating windows / split screen;
// Dual-apps events (user 999) are not visible to user 0.
class UsageReader(private val ctx: Context) : UsagePort {
    companion object {
        val LOOKBACK: Duration = Duration.ofHours(24)
    }

    private val usm = ctx.getSystemService(UsageStatsManager::class.java)
    private val um = ctx.getSystemService(UserManager::class.java)
    private val appOps = ctx.getSystemService(AppOpsManager::class.java)

    fun hasAccess(): Boolean {
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName,
        )
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                ctx.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    /** queryEvents returns EMPTY (not an exception) without access, so check first or we fail open. */
    override fun read(dayStart: Instant, now: Instant): UsageSnapshot? {
        if (!um.isUserUnlocked || !hasAccess()) return null
        return try {
            val evts = events(dayStart.minus(LOOKBACK), now)
            UsageSnapshot(UsageFolder.fold(evts, dayStart, now), UsageFolder.foreground(evts, now))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // fail closed
        }
    }

    /** Per-day per-package visible time from the OS daily aggregates (survive event pruning). API 29+. */
    fun dailyVisible(begin: Instant, end: Instant, zone: java.time.ZoneId): Map<java.time.LocalDate, Map<String, Duration>> {
        val list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin.toEpochMilli(), end.toEpochMilli())
            ?: return emptyMap()
        val out = HashMap<java.time.LocalDate, MutableMap<String, Duration>>()
        for (s in list) {
            val v = Duration.ofMillis(s.totalTimeVisible)
            if (v.isZero) continue
            val d = Instant.ofEpochMilli(s.firstTimeStamp).atZone(zone).toLocalDate()
            out.getOrPut(d) { HashMap() }.merge(s.packageName, v, Duration::plus)
        }
        return out
    }

    fun events(begin: Instant, end: Instant): List<UsageEvt> {
        val out = ArrayList<UsageEvt>()
        val q = usm.queryEvents(begin.toEpochMilli(), end.toEpochMilli())
        val e = UsageEvents.Event()
        while (q.hasNextEvent()) {
            q.getNextEvent(e)
            val type = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvt.Type.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEvt.Type.PAUSED
                UsageEvents.Event.ACTIVITY_STOPPED -> UsageEvt.Type.STOPPED
                UsageEvents.Event.SCREEN_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_HIDDEN,
                -> UsageEvt.Type.OPEN_GATE
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN,
                UsageEvents.Event.KEYGUARD_SHOWN,
                -> UsageEvt.Type.CLOSE_ALL
                else -> continue
            }
            out.add(UsageEvt(e.packageName ?: "", type, Instant.ofEpochMilli(e.timeStamp), e.className ?: ""))
        }
        return out
    }
}
