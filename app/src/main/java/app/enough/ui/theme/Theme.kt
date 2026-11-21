package app.enough.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class EnoughColors(
    val bg: Color, val fg: Color, val red: Color, val dim: Color, val line: Color, val scrim: Color,
)

val DarkColors = EnoughColors(
    Color.Black, Color.White, Color(0xFFFF2A1F), Color(0xFF8C8C8C), Color(0xFF2E2E2E), Color.Black.copy(alpha = .78f),
)
val LightColors = EnoughColors(
    Color.White, Color.Black, Color(0xFFFF2A1F), Color(0xFF6B6B6B), Color(0xFFD6D6D6), Color.White.copy(alpha = .82f),
)
val LocalEnoughColors = staticCompositionLocalOf { DarkColors }

object Dims {
    val Border = 2.dp
    val Hair = 1.dp
    val Pad = 20.dp
    val Btn = 56.dp
    val BtnSm = 48.dp
    val Touch = 48.dp
    val Icon = 40.dp
}

@Composable
fun EnoughTheme(light: Boolean = !isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (light) LightColors else DarkColors
    CompositionLocalProvider(LocalEnoughColors provides colors) {
        Box(Modifier.fillMaxSize().background(colors.bg)) { content() }
    }
}
