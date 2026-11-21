package app.enough

import android.app.Application
import app.enough.admin.Policy
import app.enough.data.ConfigStore
import app.enough.oem.OemHelper
import app.enough.service.Notifier
import app.enough.setup.SetupSession
import app.enough.usage.AppCatalog
import app.enough.usage.UsageReader
import app.enough.usage.HistoryReader
import app.enough.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EnoughApp : Application() {
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
    val controller by lazy { Controller(app, store, policy, onReleased = { notifier.released() }) }
    val history by lazy { HistoryReader(usage, catalog) }
    val setup by lazy { SetupSession(app) }
    val updater by lazy { Updater(app) }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
