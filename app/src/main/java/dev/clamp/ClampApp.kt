package dev.clamp

import android.app.Application
import dev.clamp.admin.Policy
import dev.clamp.data.ConfigStore
import dev.clamp.oem.OemHelper
import dev.clamp.service.Notifier
import dev.clamp.setup.SetupSession
import dev.clamp.usage.AppCatalog
import dev.clamp.usage.UsageReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ClampApp : Application() {
    val graph by lazy { Graph(this) }

    override fun onCreate() {
        super.onCreate()
        // Never touch DataStore here: this also runs in direct boot.
        Notifier.ensureChannels(this)
    }
}

class Graph(app: Application) {
    val store by lazy { ConfigStore(app) }
    val policy by lazy { Policy(app) }
    val usage by lazy { UsageReader(app) }
    val catalog by lazy { AppCatalog(app) }
    val notifier by lazy { Notifier(app, catalog) }
    val oem by lazy { OemHelper(app) }
    val controller by lazy { Controller(app, store, policy) }
    val history by lazy { dev.clamp.usage.HistoryReader(usage, catalog) }
    val setup by lazy { SetupSession(app) }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
