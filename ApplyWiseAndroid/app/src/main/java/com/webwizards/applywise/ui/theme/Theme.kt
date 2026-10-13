package com.webwizards.applywise.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Brand palette from the Apply-Wise website (sage, lime, cream, orange accent). */
private val Sage = Color(0xFF2D6A4F)
private val SageDark = Color(0xFF1D4D39)
private val Lime = Color(0xFF74C69D)
private val Accent = Color(0xFFFF6B35)
private val Cream = Color(0xFFF8F5EF)
private val Sand = Color(0xFFEAE4D8)
private val Ink = Color(0xFF0D1117)

private val LightColors = lightColorScheme(
    primary = Sage, onPrimary = Color.White,
    primaryContainer = Color(0xFFC6EBD6), onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6355), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8D6), onSecondaryContainer = Color(0xFF0B1F14),
    tertiary = Color(0xFFA63C11), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCF), onTertiaryContainer = Color(0xFF390C00),
    background = Cream, onBackground = Ink,
    surface = Cream, onSurface = Ink,
    surfaceVariant = Sand, onSurfaceVariant = Color(0xFF4A4F4B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFAF6),
    surfaceContainer = Color(0xFFF3EFE7),
    surfaceContainerHigh = Color(0xFFEDE8DF),
    surfaceContainerHighest = Color(0xFFE7E1D7),
    outline = Color(0xFF777C78), outlineVariant = Color(0xFFD8D2C6),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Lime, onPrimary = Color(0xFF00391F),
    primaryContainer = SageDark, onPrimaryContainer = Color(0xFFC6EBD6),
    secondary = Color(0xFFB4CCBA), onSecondary = Color(0xFF203528),
    secondaryContainer = Color(0xFF364B3E), onSecondaryContainer = Color(0xFFD0E8D6),
    tertiary = Color(0xFFFFB59A), onTertiary = Color(0xFF5B1A00),
    tertiaryContainer = Color(0xFF802A00), onTertiaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF101512), onBackground = Color(0xFFE0E4DF),
    surface = Color(0xFF101512), onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF404943), onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color(0xFF0B0F0D),
    surfaceContainerLow = Color(0xFF181D1A),
    surfaceContainer = Color(0xFF1C211E),
    surfaceContainerHigh = Color(0xFF262B28),
    surfaceContainerHighest = Color(0xFF313633),
    outline = Color(0xFF8A938C), outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

/** Extra semantic colours for match badges, readable in light and dark themes. */
@Immutable
data class MatchColors(val strong: Color, val good: Color, val reach: Color, val accent: Color)

val LocalMatchColors = staticCompositionLocalOf {
    MatchColors(strong = Sage, good = Color(0xFF0369A1), reach = Color(0xFFB45309), accent = Accent)
}

private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Brand colours by default. Pass dynamicColor = true to follow the user's
 * wallpaper (Material You) on Android 12+.
 */
@Composable
fun ApplyWiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val matchColors = if (darkTheme) MatchColors(Lime, Color(0xFF7CC4F0), Color(0xFFFBBF6A), Color(0xFFFFB59A))
    else MatchColors(Sage, Color(0xFF0369A1), Color(0xFFB45309), Accent)

    androidx.compose.runtime.CompositionLocalProvider(LocalMatchColors provides matchColors) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
