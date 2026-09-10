package pm.antani.resentin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ResentinPrimaryContainer,
    onPrimary = ResentinOnPrimaryContainer,
    primaryContainer = ResentinPrimary,
    onPrimaryContainer = ResentinOnPrimary,
    secondary = ResentinSecondaryContainer,
    onSecondary = ResentinOnSecondaryContainer,
    secondaryContainer = ResentinSecondary,
    onSecondaryContainer = ResentinOnSecondary,
    tertiary = ResentinTertiaryContainer,
    onTertiary = ResentinOnTertiaryContainer,
    tertiaryContainer = ResentinTertiary,
    onTertiaryContainer = ResentinOnTertiary,
    background = ResentinDarkBackground,
    onBackground = ResentinDarkOnSurface,
    surface = ResentinDarkSurface,
    onSurface = ResentinDarkOnSurface,
    surfaceVariant = ResentinDarkSurfaceVariant,
    onSurfaceVariant = ResentinDarkOnSurfaceVariant,
    outline = ResentinDarkOutline,
)

private val LightColorScheme = lightColorScheme(
    primary = ResentinPrimary,
    onPrimary = ResentinOnPrimary,
    primaryContainer = ResentinPrimaryContainer,
    onPrimaryContainer = ResentinOnPrimaryContainer,
    secondary = ResentinSecondary,
    onSecondary = ResentinOnSecondary,
    secondaryContainer = ResentinSecondaryContainer,
    onSecondaryContainer = ResentinOnSecondary,
    tertiary = ResentinTertiary,
    onTertiary = ResentinOnTertiary,
    tertiaryContainer = ResentinTertiaryContainer,
    onTertiaryContainer = ResentinOnTertiaryContainer,
    background = ResentinLightBackground,
    onBackground = ResentinLightOnSurface,
    surface = ResentinLightSurface,
    onSurface = ResentinLightOnSurface,
    surfaceVariant = ResentinLightSurfaceVariant,
    onSurfaceVariant = ResentinLightOnSurfaceVariant,
    outline = ResentinLightOutline,
)

private val ResentinShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun ResentinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography.scaled(fontScale),
        shapes = ResentinShapes,
        content = content,
    )
}

/** Global text-size multiplier (see the font-size slider in Settings): scales every
 * type style's size and line height, leaving the custom palette untouched. */
private fun Typography.scaled(scale: Float): Typography {
    if (scale == 1f) return this
    fun TextStyle.scaled(): TextStyle =
        copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return copy(
        displayLarge = displayLarge.scaled(),
        displayMedium = displayMedium.scaled(),
        displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(),
        headlineMedium = headlineMedium.scaled(),
        headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(),
        titleMedium = titleMedium.scaled(),
        titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(),
        bodyMedium = bodyMedium.scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(),
        labelMedium = labelMedium.scaled(),
        labelSmall = labelSmall.scaled(),
    )
}
