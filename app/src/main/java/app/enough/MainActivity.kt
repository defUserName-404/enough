package app.enough

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.enough.core.AppInfo
import app.enough.core.Change
import app.enough.core.Enforcer
import app.enough.core.Phase
import app.enough.core.PickerFilter
import app.enough.core.Target
import app.enough.core.UsageSnapshot
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.enough.service.Notifier
import app.enough.usage.HistoryResult
import app.enough.ui.EditLimitSheet
import app.enough.ui.ErrorScreen
import app.enough.ui.ReleasingScreen
import app.enough.ui.HomeScreen
import app.enough.ui.SettingsScreen
import app.enough.ui.UsageScreen
import app.enough.ui.OnboardingAppsScreen
import app.enough.ui.OnboardingLockScreen
import app.enough.ui.PhoneSetupUi
import app.enough.ui.PickerMode
import app.enough.ui.theme.EnoughTheme
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { APPS, LOCK, HOME, ADD, SETTINGS, USAGE }

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)
    private val notifDenied = mutableStateOf(false)
    private val notifPerm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginSetup() else notifDenied.value = true
    }

    private fun beginSetup() {
        (applicationContext as EnoughApp).graph.setup.start()
        launchSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EnoughTheme { Box(Modifier.fillMaxSize().systemBarsPadding()) { Root() } } }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // Revoked-access notification lands here (explicit PendingIntent), then we open usage settings.
    private fun handleIntent(intent: Intent) {
        if (intent.action == Notifier.ACTION_OPEN_USAGE) {
            intent.action = null // don't re-run on recreation
            openUsageSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++ // re-check usage access and device owner
    }

    private fun launchSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun openUsageSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    @Composable
    private fun Root() {
        val graph = (LocalContext.current.applicationContext as EnoughApp).graph
        val controller = graph.controller
        val scope = rememberCoroutineScope()
        var loadFailed by remember { mutableStateOf(false) }
        val cfgFlow = remember { graph.store.flow.catch { Log.e("Enough", "config unreadable", it); loadFailed = true } }
        val cfgState by cfgFlow.collectAsState(initial = null)
        if (loadFailed) { ErrorScreen(); return }
        val cfg = cfgState ?: return
        val tick = resumeTick.intValue

        var setupStep by rememberSaveable { mutableStateOf<Screen?>(null) }
        var adding by rememberSaveable { mutableStateOf(false) }
        var homeSub by rememberSaveable { mutableStateOf<Screen?>(null) } // SETTINGS / USAGE over Home
        var launchChecked by rememberSaveable { mutableStateOf(false) }
        var editing by remember { mutableStateOf<Target?>(null) }
        var editingNew by remember { mutableStateOf(false) }
        val setupView by graph.setup.view.collectAsState()
        var oemDone by rememberSaveable { mutableStateOf(listOf<String>()) }

        LaunchedEffect(cfg.phase, tick, setupView.state) {
            when (cfg.phase) {
                Phase.RELEASING -> while (!controller.runRelease()) delay(5_000)
                Phase.ACTIVE -> { setupStep = null; controller.startService() }
                Phase.SETUP -> {
                    adding = false; editing = null; homeSub = null
                    if (graph.policy.isActive()) {
                        // Same gate as the lock screen: never while adb-off is pending.
                        while (!graph.setup.canActivate()) delay(1_000)
                        controller.onDeviceOwnerDetected()
                    }
                }
            }
        }

        if (cfg.phase == Phase.RELEASING) { ReleasingScreen(cfg.releaseFailed); return }

        val screen = when (cfg.phase) {
            Phase.ACTIVE -> if (adding) Screen.ADD else homeSub ?: Screen.HOME
            else -> setupStep ?: if (cfg.targets.isEmpty()) Screen.APPS else Screen.LOCK
        }

        BackHandler(enabled = editing != null) { editing = null }
        BackHandler(enabled = screen == Screen.LOCK) { setupStep = Screen.APPS }
        BackHandler(enabled = screen == Screen.ADD) { adding = false }
        BackHandler(enabled = screen == Screen.SETTINGS || screen == Screen.USAGE) { homeSub = null }

        // Picker list, loaded on IO.
        var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
        val picking = screen == Screen.APPS || screen == Screen.ADD
        LaunchedEffect(picking, cfg.unsuspendable) {
            if (picking) {
                apps = withContext(Dispatchers.IO) {
                    PickerFilter.visible(graph.catalog.launchable(), graph.catalog.unpickable(cfg), "")
                }
            }
        }

        when (screen) {
            Screen.APPS -> OnboardingAppsScreen(
                PickerMode.SETUP, cfg.targets, apps,
                onDone = { picked -> scope.launch { controller.saveSetupTargets(picked); setupStep = Screen.LOCK } },
                onBack = null,
            )
            Screen.LOCK -> {
                val items = remember { graph.oem.items() }
                val granted = remember(tick) { graph.usage.hasAccess() }
                val done = oemDone.toSet() + items.filter { it.spec.id == "battery" && graph.oem.batteryUnrestricted() }.map { it.spec.id }
                val env = remember { app.enough.setup.SetupEnv(this@MainActivity) }
                val wifi = remember(tick) { env.wifi() }
                val adbWifi = remember(tick) { env.adbWifi() }
                var accountsConfirmed by rememberSaveable { mutableStateOf(false) }
                OnboardingLockScreen(
                    usageGranted = granted, oemItems = items, oemDone = done,
                    phone = PhoneSetupUi(wifi, adbWifi, accountsConfirmed, setupView, notifDenied.value),
                    onAccountsToggle = { accountsConfirmed = !accountsConfirmed },
                    onOpenAccounts = { launchSettings(Settings.ACTION_SYNC_SETTINGS) },
                    onOpenDevSettings = { launchSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
                    onPair = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifDenied.value = false
                            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else beginSetup()
                    },
                    onCancelSetup = { graph.setup.cancel() },
                    onUsage = ::openUsageSettings,
                    onOem = { item -> graph.oem.launch(this@MainActivity, item); oemDone = oemDone + item.spec.id },
                    onBack = { setupStep = Screen.APPS },
                    onActivated = {},
                )
            }
            Screen.ADD -> {
                val taken = cfg.targets.map { it.pkg }.toSet()
                val avail = remember(apps, taken) { apps.filter { it.pkg !in taken } }
                OnboardingAppsScreen(
                    PickerMode.ADD, emptyList(), avail,
                    onDone = { picked ->
                        scope.launch {
                            picked.forEach { controller.setLimit(it.pkg, it.limitMin) }
                            adding = false
                        }
                    },
                    onBack = { adding = false },
                )
            }
            Screen.HOME, Screen.SETTINGS, Screen.USAGE -> {
                var usage by remember { mutableStateOf<UsageSnapshot?>(UsageSnapshot(emptyMap(), null)) }
                var now by remember { mutableStateOf(Instant.now()) }
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val updateState by graph.updater.state.collectAsState()
                var history by remember { mutableStateOf<app.enough.core.History?>(null) }
                var historyNoAccess by remember { mutableStateOf(false) }
                var historyLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                // All Home loops run only while the activity is STARTED, and reload on every resume.
                LaunchedEffect(Unit) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        launch {
                            var n = 0
                            while (true) {
                                val clock = Clock.systemDefaultZone()
                                usage = withContext(Dispatchers.IO) {
                                    graph.usage.read(Enforcer(clock).dayStart(), clock.instant())
                                }
                                now = Instant.now()
                                // History is heavier: reload on resume and every 60 s (12 x 5 s). Keep the last good value.
                                if (n++ % 12 == 0) {
                                    when (val r = graph.history.load()) {
                                        is HistoryResult.Ok -> { history = r.history; historyLabels = r.labels; historyNoAccess = false }
                                        HistoryResult.NoAccess -> { history = null; historyNoAccess = true }
                                        HistoryResult.Error -> Unit
                                    }
                                }
                                delay(5_000)
                            }
                        }
                        launch { while (true) { delay(30_000); now = Instant.now() } } // minute precision
                    }
                }
                // Binder calls (labels, default launcher/dialer lookups) stay off the main thread.
                val unpickable by produceState(emptySet<String>(), cfg.unsuspendable) {
                    value = withContext(Dispatchers.IO) { graph.catalog.unpickable(cfg) }
                }
                val targetLabels by produceState(emptyMap<String, String>(), cfg.targets) {
                    value = withContext(Dispatchers.IO) { cfg.targets.associate { it.pkg to graph.catalog.label(it.pkg) } }
                }
                val labels = historyLabels + targetLabels
                // A new-limit sheet closes itself if the target appears externally.
                LaunchedEffect(editing, editingNew, cfg.targets) {
                    val e = editing
                    if (e != null && editingNew && cfg.target(e.pkg) != null) editing = null
                }
                // Silent update check once per launch; the Home banner appears only if a newer release exists.
                LaunchedEffect(Unit) {
                    if (!launchChecked) { launchChecked = true; graph.updater.check() }
                }
                when (screen) {
                    Screen.SETTINGS -> SettingsScreen(
                        config = cfg, updateState = updateState,
                        onCheckUpdate = { scope.launch { graph.updater.check() } },
                        onInstallUpdate = { info -> scope.launch { graph.updater.install(info) } },
                        onRelease = { scope.launch { controller.submit(Change.Release) } },
                        onBack = { homeSub = null },
                    )
                    Screen.USAGE -> UsageScreen(
                        history = history, noAccess = historyNoAccess, labels = labels, config = cfg,
                        used = usage?.usage, now = now, unpickable = unpickable,
                        onSetLimit = { pkg -> editing = Target(pkg, 30); editingNew = true },
                        onBack = { homeSub = null },
                    )
                    else -> HomeScreen(
                        config = cfg, usage = usage, labels = labels, now = now,
                        onEdit = { editing = it; editingNew = false },
                        onAdd = { adding = true },
                        onCancel = { id -> scope.launch { controller.cancel(id) } },
                        updateState = updateState,
                        onInstallUpdate = { info -> scope.launch { graph.updater.install(info) } },
                        history = history, tab = tab, onTab = { tab = it },
                        onOpenSettings = { homeSub = Screen.SETTINGS },
                        onOpenUsage = { homeSub = Screen.USAGE },
                    )
                }
                editing?.let { t ->
                    val key = "pkg:${t.pkg}"
                    EditLimitSheet(
                        target = cfg.target(t.pkg) ?: t,
                        label = labels[t.pkg] ?: t.pkg,
                        isNew = editingNew,
                        pending = cfg.pending.firstOrNull { app.enough.core.ChangePolicy.keyOf(it.change) == key },
                        now = now,
                        onSubmit = { m -> scope.launch { controller.setLimit(t.pkg, m) }; editing = null },
                        onRemove = { scope.launch { controller.submit(Change.Remove(t.pkg)) }; editing = null },
                        onClose = { editing = null },
                    )
                }
            }
        }
    }
}
