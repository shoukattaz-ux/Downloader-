package com.stashly.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Brand {
    val Violet = Color(0xFF6C4DFF)
    val VioletLight = Color(0xFF9A85FF)
    val Teal = Color(0xFF14B8A6)
    val Amber = Color(0xFFF59E0B)
    val Sky = Color(0xFF3B82F6)
    val Rose = Color(0xFFF43F5E)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF6C4DFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E4FF), onPrimaryContainer = Color(0xFF241A66),
    secondary = Color(0xFF0F9F90), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F4EE), onSecondaryContainer = Color(0xFF053B35),
    tertiary = Color(0xFFD97706), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBCB), onTertiaryContainer = Color(0xFF4A2A00),
    background = Color(0xFFF7F6FF), onBackground = Color(0xFF14122B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF14122B),
    surfaceVariant = Color(0xFFEFEDFB), onSurfaceVariant = Color(0xFF5E5B7A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBFAFF),
    surfaceContainer = Color(0xFFF3F1FD), surfaceContainerHigh = Color(0xFFECE9FA), surfaceContainerHighest = Color(0xFFE5E2F6),
    outline = Color(0xFF8B88A6), outlineVariant = Color(0xFFDAD7EE),
    error = Color(0xFFD92D3A), onError = Color.White,
    errorContainer = Color(0xFFFDE7E9), onErrorContainer = Color(0xFF5C0A12),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB3A3FF), onPrimary = Color(0xFF1E0F66),
    primaryContainer = Color(0xFF3A2C9A), onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = Color(0xFF5EE0CF), onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0B4A41), onSecondaryContainer = Color(0xFFBDF5EC),
    tertiary = Color(0xFFFFB950), onTertiary = Color(0xFF442900),
    tertiaryContainer = Color(0xFF5E3A00), onTertiaryContainer = Color(0xFFFFE2B5),
    background = Color(0xFF0F0E1C), onBackground = Color(0xFFECEAFB),
    surface = Color(0xFF17152B), onSurface = Color(0xFFECEAFB),
    surfaceVariant = Color(0xFF262341), onSurfaceVariant = Color(0xFFB4B1D0),
    surfaceContainerLowest = Color(0xFF0B0A16), surfaceContainerLow = Color(0xFF131227),
    surfaceContainer = Color(0xFF1B1930), surfaceContainerHigh = Color(0xFF242140), surfaceContainerHighest = Color(0xFF2D2A4C),
    outline = Color(0xFF8B88A9), outlineVariant = Color(0xFF3A3760),
    error = Color(0xFFFF8A93), onError = Color(0xFF5C0A12),
    errorContainer = Color(0xFF7A1B25), onErrorContainer = Color(0xFFFFDADD),
)

private val base = TextStyle(fontFamily = FontFamily.SansSerif)
private val AppTypography = Typography(
    displaySmall = base.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)

object Spacing { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp }

@Composable
fun StashlyTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography, shapes = AppShapes, content = content,
    )
}
