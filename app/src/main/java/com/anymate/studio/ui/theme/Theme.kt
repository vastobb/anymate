package com.anymate.studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.R

val Ink950 = Color(0xFF0C0B0F)
val Ink900 = Color(0xFF100E15)
val Ink850 = Color(0xFF15121C)
val Ink800 = Color(0xFF1B1724)
val Ink750 = Color(0xFF211C2C)
val Ink700 = Color(0xFF2A2437)

val Violet = Color(0xFFA78BFA)
val VioletDeep = Color(0xFF7C3AED)
val Indigo = Color(0xFF6366F1)
val Cyan = Color(0xFF22D3EE)
val Teal = Color(0xFF2DD4BF)

val TextPrimary = Color(0xFFF7F4FF)
val TextSecondary = Color(0xFFB0A9BF)
val TextMuted = Color(0xFF7D768C)
val Hairline = Color(0x1FFFFFFF)
val HairlineStrong = Color(0x33FFFFFF)

val Success = Color(0xFF34D399)
val Danger = Color(0xFFFB7185)
val Amber = Color(0xFFFBBF24)
val CanvasPaper = Color(0xFFF7F4EC)

val AccentGradient = Brush.linearGradient(listOf(VioletDeep, Indigo, Cyan))
val WarmGradient = Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFFB923C)))
val GlassGradient = Brush.verticalGradient(
    listOf(Color(0xF21B1724), Color(0xF214111C))
)

val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
    Font(R.font.inter, FontWeight.ExtraBold)
)

val Mono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold)
)

private val StudioColorScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF160B2B),
    primaryContainer = Color(0xFF3A2966),
    onPrimaryContainer = Color(0xFFEDE5FF),
    secondary = Cyan,
    onSecondary = Color(0xFF04272D),
    secondaryContainer = Color(0xFF123840),
    onSecondaryContainer = Color(0xFFC9FBFF),
    tertiary = Teal,
    onTertiary = Color(0xFF03251F),
    background = Ink950,
    onBackground = TextPrimary,
    surface = Ink850,
    onSurface = TextPrimary,
    surfaceVariant = Ink800,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A3446),
    outlineVariant = Hairline,
    error = Danger,
    onError = Color(0xFF2A0610)
)

private val StudioTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold,
        fontSize = 46.sp, lineHeight = 48.sp, letterSpacing = (-1.8).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1.2).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 1.1.sp
    )
)

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AnyMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
