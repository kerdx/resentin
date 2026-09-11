package pm.antani.resentin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pm.antani.resentin.R
import pm.antani.resentin.data.prefs.AppFontFamily

val LocalResentinFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
val LocalResentinCodeFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

private val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal))
private val FiraCode = FontFamily(Font(R.font.fira_code_regular, FontWeight.Normal))
private val SourceCodePro = FontFamily(Font(R.font.source_code_pro_regular, FontWeight.Normal))
private val IbmPlexMono = FontFamily(Font(R.font.ibm_plex_mono_regular, FontWeight.Normal))
private val CascadiaCode = FontFamily(Font(R.font.cascadia_code_regular, FontWeight.Normal))
private val Hack = FontFamily(Font(R.font.hack_regular, FontWeight.Normal))

fun AppFontFamily.toComposeFontFamily(): FontFamily = when (this) {
    AppFontFamily.SYSTEM -> FontFamily.Default
    AppFontFamily.JETBRAINS_MONO -> JetBrainsMono
    AppFontFamily.FIRA_CODE -> FiraCode
    AppFontFamily.SOURCE_CODE_PRO -> SourceCodePro
    AppFontFamily.IBM_PLEX_MONO -> IbmPlexMono
    AppFontFamily.CASCADIA_CODE -> CascadiaCode
    AppFontFamily.HACK -> Hack
}

fun AppFontFamily.toComposeCodeFontFamily(): FontFamily = when (this) {
    AppFontFamily.SYSTEM -> FontFamily.Monospace
    else -> toComposeFontFamily()
}

fun typographyFor(fontFamily: FontFamily): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
)
