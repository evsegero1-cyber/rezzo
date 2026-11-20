package ru.rezzo.academy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.rezzo.academy.core.model.Confidence

object RezzoColors {
    val Graphite = Color(0xFF0E1013)
    val Surface1 = Color(0xFF15181C)
    val Surface2 = Color(0xFF1C2026)
    val Surface3 = Color(0xFF252A31)
    val Outline = Color(0xFF2E343C)
    val Accent = Color(0xFF34D9C7)
    val AccentDim = Color(0xFF16564F)
    val Amber = Color(0xFFF2B33D)
    val Danger = Color(0xFFFF6B5E)
    val Green = Color(0xFF4CC38A)
    val Blue = Color(0xFF6AA9FF)
    val Violet = Color(0xFFB59CFF)
    val Text = Color(0xFFE7EAEE)
    val TextDim = Color(0xFF9AA4AF)
    val Scrim = Color(0xCC0E1013)

    fun confidence(c: Confidence): Color = when (c) {
        Confidence.CONFIRMED -> Green
        Confidence.VARIANT -> Blue
        Confidence.RELATED -> Violet
        Confidence.UNCONFIRMED -> Amber
        Confidence.GENERAL -> TextDim
    }
}

private val scheme = darkColorScheme(
    primary = RezzoColors.Accent,
    onPrimary = Color(0xFF00201C),
    primaryContainer = RezzoColors.AccentDim,
    onPrimaryContainer = Color(0xFFCFFBF5),
    secondary = RezzoColors.Amber,
    onSecondary = Color(0xFF2A1B00),
    background = RezzoColors.Graphite,
    onBackground = RezzoColors.Text,
    surface = RezzoColors.Surface1,
    onSurface = RezzoColors.Text,
    surfaceVariant = RezzoColors.Surface2,
    onSurfaceVariant = RezzoColors.TextDim,
    outline = RezzoColors.Outline,
    outlineVariant = RezzoColors.Outline,
    error = RezzoColors.Danger,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

@Composable
fun RezzoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
