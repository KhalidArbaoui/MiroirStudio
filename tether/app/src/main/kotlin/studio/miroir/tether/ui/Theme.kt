package studio.miroir.tether.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Neutral greys like Lightroom / Capture One: a tinted interface shifts how colour in the
 * photo is perceived. MIROIR gold is used only as the accent (selection, active, shutter).
 */
object C {
    val Ink = Color(0xFF0E0E0F)
    val Panel = Color(0xFF17171A)
    val Raised = Color(0xFF212124)
    val Hover = Color(0xFF2A2A2E)
    val Line = Color(0xFF2F2F33)
    val Cream = Color(0xFFE7E7E7)
    val Muted = Color(0xFF8B8B90)
    val Faint = Color(0xFF5C5C61)
    val Gold = Color(0xFFE6C891)
    val GoldDim = Color(0x33E6C891)
    val Ok = Color(0xFF5BD18B)
    val Warn = Color(0xFFFFB547)
    val Bad = Color(0xFFFF5A4E)

    /** Neutral (no colour cast) backgrounds for judging colour, like Lightroom's lights. */
    val ViewerBackgrounds = listOf(Color(0xFF0B0B0B), Color(0xFF2B2B2B), Color(0xFF777777))
}

/** Four-size type scale. Numbers use tabular figures so values don't shift as they change. */
object T {
    private const val TABULAR = "tnum"

    /** Small uppercase labels: SHUTTER, ISO, HISTOGRAM. */
    val Label = TextStyle(fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium, color = C.Muted)

    /** Secondary text and metadata. */
    val Small = TextStyle(fontSize = 12.sp, color = C.Muted, fontFeatureSettings = TABULAR)

    /** Body text and buttons. */
    val Body = TextStyle(fontSize = 14.sp, color = C.Cream)

    /** Exposure values in the tether bar. */
    val Value = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = C.Cream, fontFeatureSettings = TABULAR)

    /** Screen titles. */
    val Title = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Light, color = C.Gold)
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
