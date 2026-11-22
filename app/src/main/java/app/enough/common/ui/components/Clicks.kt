package app.enough.common.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

/** Wraps [onClick] with a short haptic tick (respects the system "touch feedback" setting). */
@Composable
fun hapticClick(kind: Int = HapticFeedbackConstants.VIRTUAL_KEY, onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return { view.performHapticFeedback(kind); onClick() }
}

/** Clickable without ripple/indication, with haptics. */
@Composable
fun Modifier.plainClickable(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val click = hapticClick(onClick = onClick)
    return clickable(interactionSource = src, indication = null, enabled = enabled, role = role, onClick = click)
}
