package studio.miroir.tether.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val Ink = Color(0xFF0A0806)
    val Panel = Color(0xFF14110D)
    val Raised = Color(0xFF1E1A15)
    val Line = Color(0xFF2E2820)
    val Cream = Color(0xFFE8E2D6)
    val Gold = Color(0xFFF5DFB7)
    val Muted = Color(0xFF8E867A)
    val Ok = Color(0xFF5BD18B)
    val Warn = Color(0xFFFFB547)
    val Bad = Color(0xFFFF5A4E)

    /** Neutral (no colour cast) backgrounds for judging colour, like Lightroom's lights. */
    val ViewerBackgrounds = listOf(Color(0xFF101010), Color(0xFF2B2B2B), Color(0xFF777777))
}

@Composable
fun MiroirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Gold,
            onPrimary = C.Ink,
            background = C.Ink,
            surface = C.Panel,
            onSurface = C.Cream,
            onBackground = C.Cream,
            surfaceVariant = C.Raised,
            outline = C.Line,
        ),
        content = content,
    )
}
