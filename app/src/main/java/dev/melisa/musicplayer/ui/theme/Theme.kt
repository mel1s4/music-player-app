package dev.melisa.musicplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * "Grid runner" — a cyberpunk palette built around a single electric green.
 *
 * Two rules keep it usable rather than a neon soup:
 *  - Green is reserved for state and action (playing, selected, primary button).
 *    Everything structural is a desaturated green-grey so the accent still reads.
 *  - Text colours are deliberately brighter than Material's defaults. The muted
 *    grey-on-black that looks cool on a poster fails the 4.5:1 rule and is
 *    unreadable for older eyes; every pairing here clears AA at body size.
 */
object Cyber {
    /** The signature neon. Used for primary actions, live indicators and focus. */
    val Green = Color(0xFF45F5A0)

    /** Lighter tint for text drawn on dark surfaces where pure green is too hot. */
    val GreenSoft = Color(0xFF9CFFC9)

    /** Acid lime: the "second voice", used for ranks, tags and highlights. */
    val Lime = Color(0xFFC9FF57)

    /** Cyan: albums and artists, so browsing reads differently from playing. */
    val Cyan = Color(0xFF5CE1FF)

    /** Magenta: destructive actions and the "disliked" lane. */
    val Magenta = Color(0xFFFF6B8B)

    /** Amber: warnings and "still scanning". */
    val Amber = Color(0xFFFFC34D)

    /** The void the whole UI floats in. */
    val Void = Color(0xFF060B09)

    /** Panel colour one step above the void. */
    val Panel = Color(0xFF0A1310)
}

private val DarkColors = darkColorScheme(
    primary = Cyber.Green,
    onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF0C3A25),
    onPrimaryContainer = Cyber.GreenSoft,
    inversePrimary = Color(0xFF00734A),
    secondary = Cyber.Lime,
    onSecondary = Color(0xFF1A2300),
    secondaryContainer = Color(0xFF2A3A0B),
    onSecondaryContainer = Color(0xFFE6FFB0),
    tertiary = Cyber.Cyan,
    onTertiary = Color(0xFF00232B),
    tertiaryContainer = Color(0xFF073A45),
    onTertiaryContainer = Color(0xFFB5F1FF),
    background = Cyber.Void,
    onBackground = Color(0xFFE6FFF2),
    surface = Cyber.Void,
    onSurface = Color(0xFFE6FFF2),
    surfaceVariant = Color(0xFF12231C),
    onSurfaceVariant = Color(0xFFA6C4B4),
    surfaceTint = Cyber.Green,
    inverseSurface = Color(0xFFDFF5E8),
    inverseOnSurface = Color(0xFF0A1510),
    error = Cyber.Magenta,
    onError = Color(0xFF3A0010),
    errorContainer = Color(0xFF4A1020),
    onErrorContainer = Color(0xFFFFD9E1),
    outline = Color(0xFF3E5C4C),
    outlineVariant = Color(0xFF22382E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF1B3328),
    surfaceDim = Color(0xFF040907),
    surfaceContainerLowest = Color(0xFF030705),
    surfaceContainerLow = Color(0xFF0A1410),
    surfaceContainer = Color(0xFF101E19),
    surfaceContainerHigh = Color(0xFF16281F),
    surfaceContainerHighest = Color(0xFF1C3328),
)

/**
 * The light theme is not an afterthought: it is the same system in daylight, with
 * deep green ink on mint paper. Kept because the phone is often used in a bright
 * kitchen or outside, where a dark neon panel becomes a mirror.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF007A4C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6FFDF),
    onPrimaryContainer = Color(0xFF00210F),
    inversePrimary = Cyber.Green,
    secondary = Color(0xFF4F6A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDF5A8),
    onSecondaryContainer = Color(0xFF1B2500),
    tertiary = Color(0xFF00678A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC2EEFF),
    onTertiaryContainer = Color(0xFF001F2B),
    background = Color(0xFFF1FBF5),
    onBackground = Color(0xFF07130D),
    surface = Color(0xFFF1FBF5),
    onSurface = Color(0xFF07130D),
    surfaceVariant = Color(0xFFE1F0E7),
    onSurfaceVariant = Color(0xFF3B5346),
    surfaceTint = Color(0xFF007A4C),
    inverseSurface = Color(0xFF07130D),
    inverseOnSurface = Color(0xFFE6FFF2),
    error = Color(0xFFB3264A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD9E1),
    onErrorContainer = Color(0xFF3A0010),
    outline = Color(0xFF5E7A6B),
    outlineVariant = Color(0xFFBFD5C7),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF9FFFB),
    surfaceDim = Color(0xFFDBEBE1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FEFB),
    surfaceContainer = Color(0xFFE4F1E9),
    surfaceContainerHigh = Color(0xFFDAEBE1),
    surfaceContainerHighest = Color(0xFFCFE3D8),
)

/**
 * Type is set larger than Material's baseline on purpose. The default 12sp
 * caption and 14sp body are the single most common complaint from older users;
 * the smallest thing on screen here is a 13sp mono label, and all body copy is
 * 15sp or bigger.
 */
private fun cyberTypography(): Typography {
    val sans = FontFamily.Default
    val mono = FontFamily.Monospace
    return Typography(
        displayLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-0.5).sp),
        displayMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp),
        displaySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-0.25).sp),
        headlineLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
        headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
        titleLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
        titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
        titleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
        bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp, letterSpacing = 0.1.sp),
        bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
        bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp),
        labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        // Mono readouts. Tracking is deliberately light: the style comes from the
        // typeface, not from spacing letters so far apart they slow reading down.
        labelMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.1.sp),
        labelSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    )
}

private val CyberShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun MusicPlayerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = cyberTypography(),
        shapes = CyberShapes,
        content = content,
    )
}

/**
 * Keeps the system status/navigation bar icons readable against our background.
 * MusicPlayerTheme changes Compose colours only — the window is a separate system.
 */
@Composable
fun SystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    val activity = LocalActivity.current
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
}
