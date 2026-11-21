package app.enough.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Single font swap point. Fonts are not bundled (out of scope). */
object EnoughType {
    val Heading: FontFamily = FontFamily.SansSerif // swap: FontFamily(Font(R.font.archivo_black))
    val Body: FontFamily = FontFamily.SansSerif // swap: Space Grotesk
    val Mono: FontFamily = FontFamily.Monospace // swap: JetBrains Mono

    val h1 = TextStyle(fontFamily = Heading, fontWeight = FontWeight.Black, fontSize = 46.sp, lineHeight = 42.sp, letterSpacing = (-1.5).sp)
    val h2 = h1.copy(fontSize = 26.sp, lineHeight = 26.sp, letterSpacing = (-0.5).sp)
    val name = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.6.sp)
    val body = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp)
    val label = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.6.sp)
    val button = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.4.sp)
    val num = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val big = TextStyle(fontFamily = Mono, fontWeight = FontWeight.ExtraBold, fontSize = 72.sp)
}
