package dev.clamp.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dev.clamp.ClampApp
import dev.clamp.core.SetupState
import dev.clamp.service.Notifier

/**
 * Accepted risk: RemoteInput needs a MUTABLE PendingIntent. The intent is explicit (this receiver,
 * not exported) so another app can't redirect it, and the code is dropped unless the session is
 * waiting for one. Worst case a rogue sender could submit a wrong 6-digit code, which just fails pairing.
 */
// TODO(device): MIUI inline reply in the shade; fallback text in the UI points to split screen / WITH A COMPUTER.
class PairCodeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val setup = (ctx.applicationContext as ClampApp).graph.setup
        if (setup.view.value.state !is SetupState.AwaitingCode) return
        RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_CODE)?.toString()
            ?.let { setup.submitCode(it) }
    }
}
