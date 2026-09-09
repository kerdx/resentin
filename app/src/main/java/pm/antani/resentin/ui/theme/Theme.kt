package pm.antani.resentin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ResentinShapes,
        content = content,
    )
}
