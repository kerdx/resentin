package pm.antani.resentin.ui.chat

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pm.antani.resentin.BuildConfig

/**
 * The `/credits` easter egg: a full-viewport WebView playing
 * `assets/credits/index.html` — Resentin's own contributor roll (baked in at
 * build time by `scripts/credits.sh`, a GitHub Actions step) followed by a
 * replica of grappa-irc/cicchetto's own credits roll (same text, same
 * synthesised chiptune, same falling-character background).
 *
 * A WebView rather than a Kotlin/Compose port: cic's credits roll is ~3500
 * lines of hand-tuned WebAudio (a from-scratch chiptune synth) and Canvas
 * animation, refined over a dozen issues. Android's WebView already runs
 * that stack; porting it to AudioTrack/Compose Canvas would be reimplementing
 * a browser engine's audio graph by hand for one easter egg screen. The
 * assets under `app/src/main/assets/credits/` are a de-typed transliteration
 * of cic's own TypeScript (same musical data, same text, same rain
 * algorithm), not a fresh design — see that directory's `main.js` for the
 * one deliberate simplification (a fixed-speed JS-driven scroll instead of
 * cic's per-pass CSS-animation-duration measurement).
 */
@Composable
fun CreditsScreen(onClose: () -> Unit) {
    // A real Dialog window rather than a composable dropped into the chat
    // tree: `usePlatformDefaultWidth = false` buys the full-bleed viewport
    // the roll needs, and `onDismissRequest` already wires the system back
    // button to close it — no separate BackHandler required.
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(AndroidColor.BLACK)
                    // The roll runs for minutes on a fixed requestAnimationFrame clock;
                    // if the display dims/sleeps on the default screen timeout mid-pass,
                    // the WebView suspends rAF for the time it's not visible and the
                    // roll resumes desynced — read live as "only the first block plays
                    // right, the rest start mid-scroll and loop". Keeping the screen on
                    // for as long as this WebView is attached removes the cause outright.
                    keepScreenOn = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Belt and braces alongside the classic-<script> fix in
                    // index.html (module `import` across file:// origins was
                    // the actual cause of the blank page): this WebView only
                    // ever loads the one bundled asset page, never anything
                    // remote, so relaxing file-origin access costs nothing here.
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true
                    // The page's AudioContext starts on load, not on an in-page
                    // tap — the real user gesture was the /credits command
                    // itself, one layer up.
                    @Suppress("DEPRECATION")
                    settings.mediaPlaybackRequiresUserGesture = false
                    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                    addJavascriptInterface(CreditsBridge(onClose), "ResentinCredits")
                    loadCreditsPage(this)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun loadCreditsPage(webView: WebView) {
    webView.loadUrl("file:///android_asset/credits/index.html?version=${BuildConfig.VERSION_NAME}")
}

/** `main.js`'s `closeCredits()` calls `window.ResentinCredits.close()` when
 * the finale's button (or the chrome's X) is tapped. `@JavascriptInterface`
 * methods run on a binder thread, never the main thread, so this hops back
 * before touching Compose state. */
private class CreditsBridge(private val onClose: () -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun close() {
        mainHandler.post(onClose)
    }
}
