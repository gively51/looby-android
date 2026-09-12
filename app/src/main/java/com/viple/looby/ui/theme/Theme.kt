package com.viple.looby.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette de marque
val LoobyViolet = Color(0xFF5B3DF5)
val LoobyVioletDark = Color(0xFFB9A8FF)
val GivelyCoral = Color(0xFFFF6B6B)
val GivelyCoralDark = Color(0xFFFF9E9E)
val LivriaTeal = Color(0xFF0FA3A3)
val LivriaTealDark = Color(0xFF6BD8D8)
val Sunflower = Color(0xFFFFD166)

private val LightColors = lightColorScheme(
    primary = LoobyViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = Color(0xFF1A0B5C),
    secondary = GivelyCoral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD8),
    onSecondaryContainer = Color(0xFF410006),
    tertiary = LivriaTeal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCF3F3),
    onTertiaryContainer = Color(0xFF003737),
    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF1B1B22),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF1B1B22),
    surfaceVariant = Color(0xFFEEEBF7),
    onSurfaceVariant = Color(0xFF4A4657),
    surfaceContainer = Color(0xFFF3F1FA),
    surfaceContainerHigh = Color(0xFFEDEAF5),
    surfaceContainerHighest = Color(0xFFE7E4F0),
    surfaceContainerLow = Color(0xFFF7F5FD),
    outline = Color(0xFF7B7689),
    outlineVariant = Color(0xFFCBC6D8),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = LoobyVioletDark,
    onPrimary = Color(0xFF2A0F8F),
    primaryContainer = Color(0xFF4127C7),
    onPrimaryContainer = Color(0xFFE6E0FF),
    secondary = GivelyCoralDark,
    onSecondary = Color(0xFF5F0010),
    secondaryContainer = Color(0xFF8A2A32),
    onSecondaryContainer = Color(0xFFFFDAD8),
    tertiary = LivriaTealDark,
    onTertiary = Color(0xFF003737),
    tertiaryContainer = Color(0xFF00504F),
    onTertiaryContainer = Color(0xFFCCF3F3),
    background = Color(0xFF0F0E17),
    onBackground = Color(0xFFE6E3EE),
    surface = Color(0xFF0F0E17),
    onSurface = Color(0xFFE6E3EE),
    surfaceVariant = Color(0xFF2B2838),
    onSurfaceVariant = Color(0xFFCBC6D8),
    surfaceContainer = Color(0xFF1B1926),
    surfaceContainerHigh = Color(0xFF25222F),
    surfaceContainerHighest = Color(0xFF302D3B),
    surfaceContainerLow = Color(0xFF17151F),
    outline = Color(0xFF959099),
    outlineVariant = Color(0xFF4A4657),
    error = Color(0xFFFFB4AB)
)

val LoobyTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)

val LoobyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Couleurs de section (Looby / Gively / Livria) accessibles dans toute l'UI. */
data class BrandColors(
    val looby: Color,
    val gively: Color,
    val livria: Color,
    val accent: Color,
    val heroGradient: Brush,
    val givelyGradient: Brush,
    val livriaGradient: Brush
)

val LocalBrandColors = staticCompositionLocalOf {
    BrandColors(LoobyViolet, GivelyCoral, LivriaTeal, Sunflower, Brush.linearGradient(listOf(LoobyViolet, LoobyViolet)), Brush.linearGradient(listOf(GivelyCoral, GivelyCoral)), Brush.linearGradient(listOf(LivriaTeal, LivriaTeal)))
}

object LoobyTheme {
    val brand: BrandColors @Composable get() = LocalBrandColors.current
}

@Composable
fun LoobyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val brand = if (darkTheme) BrandColors(
        looby = LoobyVioletDark, gively = GivelyCoralDark, livria = LivriaTealDark, accent = Sunflower,
        heroGradient = Brush.linearGradient(listOf(Color(0xFF4127C7), Color(0xFF7B5CFF), Color(0xFFB9A8FF))),
        givelyGradient = Brush.linearGradient(listOf(Color(0xFFB83B4A), Color(0xFFFF6B6B), Color(0xFFFFA07A))),
        livriaGradient = Brush.linearGradient(listOf(Color(0xFF006B6B), Color(0xFF0FA3A3), Color(0xFF6BD8D8)))
    ) else BrandColors(
        looby = LoobyViolet, gively = GivelyCoral, livria = LivriaTeal, accent = Sunflower,
        heroGradient = Brush.linearGradient(listOf(Color(0xFF5B3DF5), Color(0xFF8B6CFF), Color(0xFFC3B3FF))),
        givelyGradient = Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFF8E72), Color(0xFFFFB199))),
        livriaGradient = Brush.linearGradient(listOf(Color(0xFF0FA3A3), Color(0xFF3CC4C4), Color(0xFF9BE3E3)))
    )

    androidx.compose.runtime.CompositionLocalProvider(LocalBrandColors provides brand) {
        MaterialTheme(colorScheme = colorScheme, typography = LoobyTypography, shapes = LoobyShapes, content = content)
    }
}
