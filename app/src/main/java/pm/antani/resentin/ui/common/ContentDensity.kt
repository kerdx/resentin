package pm.antani.resentin.ui.common

import androidx.compose.runtime.compositionLocalOf

/** Global vertical-rhythm multiplier driven by the Densità setting (Aspetto) —
 * 1 is the previous spacing everywhere. Provided once in MainActivity from the
 * stored [pm.antani.resentin.data.prefs.MessageDensity]; list rows multiply
 * their vertical paddings (and list spacings) by it so density covers the whole
 * app, not just chat bubbles. */
val LocalDensityScale = compositionLocalOf { 1f }
