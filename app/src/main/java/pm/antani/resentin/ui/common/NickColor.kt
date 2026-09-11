package pm.antani.resentin.ui.common

import androidx.compose.ui.graphics.Color

// A curated palette rather than an HSL-from-hash sweep — keeps every color legible
// on dark surfaces without a separate per-theme table, and avoids muddy/low-contrast
// hues a pure hash could land on.
private val NICK_PALETTE = listOf(
    Color(0xFFE57373),
    Color(0xFFF06292),
    Color(0xFFBA68C8),
    Color(0xFF9575CD),
    Color(0xFF7986CB),
    Color(0xFF64B5F6),
    Color(0xFF4FC3F7),
    Color(0xFF4DB6AC),
    Color(0xFF81C784),
    Color(0xFFAED581),
    Color(0xFFFFB74D),
    Color(0xFFA1887F),
)

// Same hues one step darker (Material 800/900): the 300s above fall below a
// readable contrast on near-white surfaces (light green, lime, orange and sky
// blue most of all), so light-theme callers land here instead — same slot per
// nick, so the color stays a stable visual anchor within each theme.
private val NICK_PALETTE_LIGHT = listOf(
    Color(0xFFC62828),
    Color(0xFFAD1457),
    Color(0xFF6A1B9A),
    Color(0xFF4527A0),
    Color(0xFF283593),
    Color(0xFF1565C0),
    Color(0xFF0277BD),
    Color(0xFF00695C),
    Color(0xFF2E7D32),
    Color(0xFF558B2F),
    Color(0xFFEF6C00),
    Color(0xFF4E342E),
)

/** Deterministic, stable per-nick color from [NICK_PALETTE] — the same nick always
 * lands on the same color (a pure hash, not random), so it stays a usable visual
 * anchor for "who said that" across a session and across app restarts. Case-folded
 * so `Foo`/`foo` (the same IRC identity under every common casemapping) match.
 * Pass the app's [isLightTheme] so light surfaces get the darker table. */
fun colorForNick(nick: String, lightTheme: Boolean = false): Color {
    val hash = nick.lowercase().fold(0) { acc, c -> acc * 31 + c.code }
    val palette = if (lightTheme) NICK_PALETTE_LIGHT else NICK_PALETTE
    return palette[Math.floorMod(hash, palette.size)]
}
