package com.water.widget.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.water.widget.AppThemeMode

/** Theme-aware colors for water status, feedback, and layered surfaces. */
data class WaterSemanticColors(
    val water: Color,
    val waterStrong: Color,
    val waterSoft: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val danger: Color,
    val onDanger: Color,
    val surfaceRaised: Color,
    val surfaceMuted: Color,
    val outlineSoft: Color,
    val scrim: Color
)

/** Ordered color stops for consistently rendered water-inspired gradients. */
data class WaterGradients(
    val brand: List<Color>,
    val tranquil: List<Color>,
    val progress: List<Color>,
    val sunrise: List<Color>
)

private val LightSemanticColors = WaterSemanticColors(
    water = Color(0xFF1598C9),
    waterStrong = Color(0xFF006C91),
    waterSoft = Color(0xFFD9F5FF),
    success = Color(0xFF16845B),
    onSuccess = Color.White,
    warning = Color(0xFFA85E00),
    onWarning = Color.White,
    danger = Color(0xFFB3261E),
    onDanger = Color.White,
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF0F7FA),
    outlineSoft = Color(0xFFCBD9DF),
    scrim = Color(0x99091D25)
)

private val DarkSemanticColors = WaterSemanticColors(
    water = Color(0xFF70D5FA),
    waterStrong = Color(0xFFB5ECFF),
    waterSoft = Color(0xFF163E4B),
    success = Color(0xFF70D6A8),
    onSuccess = Color(0xFF003822),
    warning = Color(0xFFFFB869),
    onWarning = Color(0xFF482400),
    danger = Color(0xFFFFB4AB),
    onDanger = Color(0xFF690005),
    surfaceRaised = Color(0xFF142A32),
    surfaceMuted = Color(0xFF0D2027),
    outlineSoft = Color(0xFF3C5660),
    scrim = Color(0xB300090C)
)

private val LightGradients = WaterGradients(
    brand = listOf(Color(0xFF007CA9), Color(0xFF36BEE7)),
    tranquil = listOf(Color(0xFFE6F9FF), Color(0xFFF7FCFD)),
    progress = listOf(Color(0xFF04A8D8), Color(0xFF70D8E9)),
    sunrise = listOf(Color(0xFFFFA75D), Color(0xFFFFD277))
)

private val DarkGradients = WaterGradients(
    brand = listOf(Color(0xFF146C8B), Color(0xFF289FC4)),
    tranquil = listOf(Color(0xFF102D37), Color(0xFF0B1D24)),
    progress = listOf(Color(0xFF29BCE6), Color(0xFF76D9EC)),
    sunrise = listOf(Color(0xFFD57A39), Color(0xFFF0A653))
)

private val LocalWaterSemanticColors = staticCompositionLocalOf { LightSemanticColors }
private val LocalWaterGradients = staticCompositionLocalOf { LightGradients }

/** Shared semantic tokens. Read [colors] and [gradients] inside a [WaterTheme]. */
object WaterThemeTokens {
    val colors: WaterSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalWaterSemanticColors.current

    val gradients: WaterGradients
        @Composable
        @ReadOnlyComposable
        get() = LocalWaterGradients.current

    val shapes: WaterShapeTokens
        get() = WaterShapeTokens

    val spacing: WaterSpacing
        get() = WaterSpacing
}

/** Consistent rounded geometry for cards, controls, sheets, and feature artwork. */
object WaterShapeTokens {
    val compact = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(20.dp)
    val prominent = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(50)

    internal val material = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = compact,
        medium = card,
        large = prominent,
        extraLarge = RoundedCornerShape(32.dp)
    )
}

/** Standard layout rhythm. Use these instead of page-specific literal spacing values. */
object WaterSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val page = 20.dp
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF007CA9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F2FF),
    onPrimaryContainer = Color(0xFF003545),
    secondary = Color(0xFF006D77),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6F3F5),
    onSecondaryContainer = Color(0xFF002021),
    tertiary = Color(0xFFA85E00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF2B1700),
    error = LightSemanticColors.danger,
    onError = LightSemanticColors.onDanger,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAFC),
    onBackground = Color(0xFF10252D),
    surface = Color(0xFFFCFEFF),
    onSurface = Color(0xFF10252D),
    surfaceVariant = Color(0xFFE2EEF2),
    onSurfaceVariant = Color(0xFF40545C),
    outline = Color(0xFF70828A),
    outlineVariant = LightSemanticColors.outlineSoft,
    scrim = LightSemanticColors.scrim
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF70D5FA),
    onPrimary = Color(0xFF003545),
    primaryContainer = Color(0xFF00506B),
    onPrimaryContainer = Color(0xFFD3F2FF),
    secondary = Color(0xFF77D6DF),
    onSecondary = Color(0xFF00373B),
    secondaryContainer = Color(0xFF004F55),
    onSecondaryContainer = Color(0xFF9CF1F5),
    tertiary = Color(0xFFFFB869),
    onTertiary = Color(0xFF482400),
    tertiaryContainer = Color(0xFF693900),
    onTertiaryContainer = Color(0xFFFFDCC0),
    error = DarkSemanticColors.danger,
    onError = DarkSemanticColors.onDanger,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF071A21),
    onBackground = Color(0xFFE7F3F7),
    surface = Color(0xFF0F252D),
    onSurface = Color(0xFFE7F3F7),
    surfaceVariant = Color(0xFF233A43),
    onSurfaceVariant = Color(0xFFB8C8CE),
    outline = Color(0xFF8B9EA6),
    outlineVariant = DarkSemanticColors.outlineSoft,
    scrim = DarkSemanticColors.scrim
)

@Composable
fun WaterTheme(
    mode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val gradients = if (darkTheme) DarkGradients else LightGradients

    CompositionLocalProvider(
        LocalWaterSemanticColors provides semanticColors,
        LocalWaterGradients provides gradients
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MaterialTheme.typography,
            shapes = WaterShapeTokens.material,
            content = content
        )
    }
}
