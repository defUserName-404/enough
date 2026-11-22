package app.enough

import android.app.Application
import app.enough.enforce.android.Notifier
import app.enough.enforce.android.Policy
import app.enough.limits.android.ConfigStore
import app.enough.oem.android.OemHelper
import app.enough.setup.android.SetupSession
import app.enough.update.android.Updater
import app.enough.usage.android.AppCatalog
import app.enough.usage.android.HistoryReader
import app.enough.usage.android.UsageReader
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
