package app.enough

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import app.enough.common.ui.theme.EnoughTheme
import app.enough.enforce.android.Notifier

/** Hosts [AppRoot]. State lives in [AppViewModel]; this class only does what needs an Activity. */
class MainActivity : ComponentActivity() {
    private val graph: Graph get() = (applicationContext as EnoughApp).graph
    private val vm: AppViewModel by viewModels { AppViewModel.factory(graph) }

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginSetup() else vm.setNotifDenied(true)
    }

    private val actions = ActivityActions(
        openSettings = ::launchSettings,
        openUsageAccess = ::openUsageSettings,
        pair = ::requestPairing,
        launchOem = { item -> graph.oem.launch(this, item) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EnoughTheme { Box(Modifier.fillMaxSize().systemBarsPadding()) { AppRoot(vm, actions) } } }
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
        vm.onResume() // re-check usage access and device owner
    }

    private fun requestPairing() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            vm.setNotifDenied(false)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else beginSetup()
    }

    private fun beginSetup() {
        graph.setup.start()
        launchSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
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
}
