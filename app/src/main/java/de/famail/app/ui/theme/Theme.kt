package de.famail.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import de.famail.app.data.settings.ThemeMode

/** Farben im Stil der Referenz: helle, leicht lavendelfarbene Fläche, Glas-Elemente, Systemblau. */
@Immutable
data class MailColors(
    val background: Color,
    val pane: Color,
    val glass: Color,
    val glassBorder: Color,
    val separator: Color,
    val accent: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val unreadDot: Color,
    val flag: Color,
    val danger: Color,
    val selectedSecondary: Color,
    val scrim: Color,
    val isDark: Boolean
)

val LightMailColors = MailColors(
    background = Color(0xFFF6F4FA),
    pane = Color(0xFFFBFAFD),
    glass = Color(0xE6FFFFFF),
    glassBorder = Color(0x14000000),
    separator = Color(0x1F3C3C43),
    accent = Color(0xFF0A7AFF),
    onAccent = Color.White,
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF8A8A8E),
    textTertiary = Color(0xFFB0B0B5),
    unreadDot = Color(0xFF0A7AFF),
    flag = Color(0xFFFF9500),
    danger = Color(0xFFFF3B30),
    selectedSecondary = Color(0xD9FFFFFF),
    scrim = Color(0x33000000),
    isDark = false
)

val DarkMailColors = MailColors(
    background = Color(0xFF000000),
    pane = Color(0xFF111113),
    glass = Color(0xE62C2C2E),
    glassBorder = Color(0x26FFFFFF),
    separator = Color(0x40545458),
    accent = Color(0xFF0A84FF),
    onAccent = Color.White,
    textPrimary = Color(0xFFF2F2F7),
    textSecondary = Color(0xFF98989F),
    textTertiary = Color(0xFF636366),
    unreadDot = Color(0xFF0A84FF),
    flag = Color(0xFFFF9F0A),
    danger = Color(0xFFFF453A),
    selectedSecondary = Color(0xD9FFFFFF),
    scrim = Color(0x66000000),
    isDark = true
)

val LocalMailColors = staticCompositionLocalOf { LightMailColors }

/** Avatar-Farben für Initialen */
val AvatarPalette = listOf(
    Color(0xFF5E5CE6), Color(0xFF30B0C7), Color(0xFFFF9F0A), Color(0xFF34C759),
    Color(0xFFFF375F), Color(0xFF0A7AFF), Color(0xFFAF52DE), Color(0xFFA2845E)
)

private val MailTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

/** Apple-typische, stark abgerundete Formen (Menüs, Dialoge, Karten). */
private val MailShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun FaMailTheme(themeMode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val mc = if (dark) DarkMailColors else LightMailColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = mc.accent, onPrimary = mc.onAccent, background = mc.background,
            surface = mc.pane, onSurface = mc.textPrimary, onBackground = mc.textPrimary,
            surfaceVariant = Color(0xFF1C1C1E), onSurfaceVariant = mc.textSecondary,
            outlineVariant = mc.separator, error = mc.danger,
            surfaceContainer = Color(0xFF2C2C2E), surfaceContainerHigh = Color(0xFF2C2C2E),
            surfaceContainerLow = Color(0xFF1C1C1E), surfaceContainerHighest = Color(0xFF3A3A3C),
            inverseSurface = Color(0xFFF2F2F7), inverseOnSurface = Color(0xFF1C1C1E)
        )
    } else {
        lightColorScheme(
            primary = mc.accent, onPrimary = mc.onAccent, background = mc.background,
            surface = mc.pane, onSurface = mc.textPrimary, onBackground = mc.textPrimary,
            surfaceVariant = Color(0xFFEFEDF4), onSurfaceVariant = mc.textSecondary,
            outlineVariant = mc.separator, error = mc.danger,
            surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
            surfaceContainerLow = Color.White, surfaceContainerHighest = Color.White,
            inverseSurface = Color(0xF21C1C1E), inverseOnSurface = Color.White
        )
    }
    // Statusleisten-Symbole passend zum hellen bzw. dunklen Design
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalMailColors provides mc,
        LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScale)
    ) {
        MaterialTheme(colorScheme = scheme, typography = MailTypography, shapes = MailShapes, content = content)
    }
}

object MailTheme {
    val colors: MailColors
        @Composable get() = LocalMailColors.current
}
