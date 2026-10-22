package com.anymate.studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.R

/**
 * AnyMate ships three art-directed colour systems. Every screen reads from the
 * active palette through the [AnyMateColors] accessors, so switching a theme
 * restyles the whole studio — canvas chrome, tools, timeline and sheets — at once.
 */
enum class ThemeKind(val label: String, val blurb: String) {
    GRAPHITE(
        "Graphite",
        "Cool neutral studio. Precise, quiet, focused on the artwork."
    ),
    WARM_NEUTRAL(
        "Warm Neutral",
        "Sand, clay and paper. Editorial and soft for hand-drawn work."
    ),
    MIDNIGHT_SLATE(
        "Midnight Slate",
        "Deep blue slate with periwinkle light. Cinematic and late-night."
    )
}

data class AnyMatePalette(
    val ink950: Color,
    val ink900: Color,
    val ink850: Color,
    val ink800: Color,
    val ink750: Color,
    val ink700: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val success: Color,
    val danger: Color,
    val amber: Color,
    val paper: Color,
    val shadow: Color
) {
    val accentGradient: Brush
        get() = Brush.linearGradient(listOf(primary, secondary))

    val warmGradient: Brush
        get() = Brush.linearGradient(listOf(tertiary, amber))

    val glassGradient: Brush
        get() = Brush.verticalGradient(listOf(ink850.copy(alpha = 0.95f), ink950.copy(alpha = 0.9f)))
}

private val GraphitePalette = AnyMatePalette(
    ink950 = Color(0xFF0E0F11),
    ink900 = Color(0xFF141518),
    ink850 = Color(0xFF1A1C20),
    ink800 = Color(0xFF212429),
    ink750 = Color(0xFF282C31),
    ink700 = Color(0xFF32363C),
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF7DD3FC),
    tertiary = Color(0xFFC4B5FD),
    textPrimary = Color(0xFFF2F4F7),
    textSecondary = Color(0xFFAEB4BE),
    textMuted = Color(0xFF7A818D),
    hairline = Color(0x1FFFFFFF),
    hairlineStrong = Color(0x33FFFFFF),
    success = Color(0xFF4ADE80),
    danger = Color(0xFFF87171),
    amber = Color(0xFFFBBF24),
    paper = Color(0xFFF4F5F7),
    shadow = Color(0xFF000000)
)

private val WarmNeutralPalette = AnyMatePalette(
    ink950 = Color(0xFF12100E),
    ink900 = Color(0xFF191614),
    ink850 = Color(0xFF201C19),
    ink800 = Color(0xFF282320),
    ink750 = Color(0xFF302A26),
    ink700 = Color(0xFF3A322C),
    primary = Color(0xFFE0A06A),
    secondary = Color(0xFFC97B5A),
    tertiary = Color(0xFFA3B18A),
    textPrimary = Color(0xFFF6F1EA),
    textSecondary = Color(0xFFC0B6A9),
    textMuted = Color(0xFF8E8478),
    hairline = Color(0x1FFFFFFF),
    hairlineStrong = Color(0x30FFFFFF),
    success = Color(0xFF86C46B),
    danger = Color(0xFFE4715B),
    amber = Color(0xFFE3B341),
    paper = Color(0xFFF6F1E7),
    shadow = Color(0xFF1A1008)
)

private val MidnightSlatePalette = AnyMatePalette(
    ink950 = Color(0xFF0A0E16),
    ink900 = Color(0xFF10151F),
    ink850 = Color(0xFF151B27),
    ink800 = Color(0xFF1C2432),
    ink750 = Color(0xFF232C3D),
    ink700 = Color(0xFF2C3648),
    primary = Color(0xFFA5B4FC),
    secondary = Color(0xFF67E8F9),
    tertiary = Color(0xFFF0ABFC),
    textPrimary = Color(0xFFEEF2FF),
    textSecondary = Color(0xFFA9B4C9),
    textMuted = Color(0xFF737F96),
    hairline = Color(0x1FFFFFFF),
    hairlineStrong = Color(0x33FFFFFF),
    success = Color(0xFF34D399),
    danger = Color(0xFFFB7185),
    amber = Color(0xFFFBBF24),
    paper = Color(0xFFEEF1F7),
    shadow = Color(0xFF00060F)
)

/**
 * Holds the palette for the current theme. The values are plain reads (not
 * snapshot state) so they can be used from draw lambdas; the whole tree is
 * re-keyed when the theme changes so every screen repaints.
 */
object AnyMateColors {
    var active: AnyMatePalette = GraphitePalette
        private set

    fun apply(kind: ThemeKind) {
        active = kind.palette
    }
}

/** Density switch so the same studio can breathe or pack. */
object AnyMateMetrics {
    var compact: Boolean = false
        private set

    fun apply(compact: Boolean) {
        this.compact = compact
    }
}

val ThemeKind.palette: AnyMatePalette
    get() = when (this) {
        ThemeKind.GRAPHITE -> GraphitePalette
        ThemeKind.WARM_NEUTRAL -> WarmNeutralPalette
        ThemeKind.MIDNIGHT_SLATE -> MidnightSlatePalette
    }

// ---- Backwards compatible accessors ---------------------------------------
val Ink950: Color get() = AnyMateColors.active.ink950
val Ink900: Color get() = AnyMateColors.active.ink900
val Ink850: Color get() = AnyMateColors.active.ink850
val Ink800: Color get() = AnyMateColors.active.ink800
val Ink750: Color get() = AnyMateColors.active.ink750
val Ink700: Color get() = AnyMateColors.active.ink700
val Violet: Color get() = AnyMateColors.active.primary
val Indigo: Color get() = mix(AnyMateColors.active.primary, AnyMateColors.active.secondary, 0.5f)
val Cyan: Color get() = AnyMateColors.active.secondary
val Teal: Color get() = AnyMateColors.active.tertiary
val TextPrimary: Color get() = AnyMateColors.active.textPrimary
val TextSecondary: Color get() = AnyMateColors.active.textSecondary
val TextMuted: Color get() = AnyMateColors.active.textMuted
val Hairline: Color get() = AnyMateColors.active.hairline
val HairlineStrong: Color get() = AnyMateColors.active.hairlineStrong
val Success: Color get() = AnyMateColors.active.success
val Danger: Color get() = AnyMateColors.active.danger
val Amber: Color get() = AnyMateColors.active.amber
val CanvasPaper: Color get() = AnyMateColors.active.paper
val AccentGradient: Brush get() = AnyMateColors.active.accentGradient
val WarmGradient: Brush get() = AnyMateColors.active.warmGradient
val GlassGradient: Brush get() = AnyMateColors.active.glassGradient

private fun mix(a: Color, b: Color, fraction: Float): Color = Color(
    red = a.red + (b.red - a.red) * fraction,
    green = a.green + (b.green - a.green) * fraction,
    blue = a.blue + (b.blue - a.blue) * fraction,
    alpha = a.alpha + (b.alpha - a.alpha) * fraction
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

private fun AnyMatePalette.toColorScheme() = darkColorScheme(
    primary = primary,
    onPrimary = ink950,
    primaryContainer = primary.copy(alpha = 0.22f),
    onPrimaryContainer = textPrimary,
    secondary = secondary,
    onSecondary = ink950,
    secondaryContainer = secondary.copy(alpha = 0.2f),
    onSecondaryContainer = textPrimary,
    tertiary = tertiary,
    onTertiary = ink950,
    background = ink950,
    onBackground = textPrimary,
    surface = ink850,
    onSurface = textPrimary,
    surfaceVariant = ink800,
    onSurfaceVariant = textSecondary,
    outline = ink700,
    outlineVariant = hairline,
    error = danger,
    onError = ink950
)

@Composable
fun AnyMateTheme(
    themeKind: ThemeKind = ThemeKind.GRAPHITE,
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    AnyMateColors.apply(themeKind)
    AnyMateMetrics.apply(compact)
    MaterialTheme(
        colorScheme = themeKind.palette.toColorScheme(),
        typography = StudioTypography,
        shapes = StudioShapes
    ) {
        key(themeKind, compact) { content() }
    }
}
