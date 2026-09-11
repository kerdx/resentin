package pm.antani.resentin.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Process-wide, in-memory only — an avatar is a small preview image re-fetched from
// [pm.antani.resentin.domain.repository.NetworksRepository.fetchAvatarBytes] (own or
// peer, both served over authenticated HTTP), so nothing here needs to survive a
// process restart. Count-bounded rather than byte-bounded: a handful of live rows on
// screen at once, never worth measuring per-bitmap.
private val avatarBitmapCache = LruCache<String, Bitmap>(64)

/**
 * Decodes and caches the avatar bitmap at [url] (a [pm.antani.resentin.data.db.NetworkEntity]'s
 * own avatar, or a DM partner's cached CTCP AVATAR on a query [pm.antani.resentin.data.db.ChannelEntity]) —
 * `null` while unset, unresolved, or still loading. [fetchBytes] is injected rather than a
 * repository reference so this stays screen-agnostic (Home's rows, a chat header, ...).
 */
@Composable
fun rememberAvatarBitmap(url: String?, fetchBytes: suspend (String) -> ByteArray?): Bitmap? {
    var bitmap by remember(url) { mutableStateOf(url?.let { avatarBitmapCache.get(it) }) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        if (avatarBitmapCache.get(url) != null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            fetchBytes(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        if (decoded != null) {
            avatarBitmapCache.put(url, decoded)
            bitmap = decoded
        }
    }
    return bitmap
}
