package pm.antani.resentin.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_prefs")

/** Local per-chat flag key, "slug/lowercased-target" — never sent to the server,
 * unlike the server-persisted display prefs. Case-folded: "#Foo" and "#foo" are the
 * same IRC target under every common casemapping. */
fun channelKey(networkSlug: String, channel: String) =
    "${networkSlug.lowercase()}/${channel.lowercase()}"

/** Purely a local rendering choice — never sent to the server, unlike e.g. the
 * server-persisted `coloredNicklist` display pref. */
enum class ChatDisplayMode {
    BUBBLES,
    IRC_LINE,
}

/** How swipe-to-reply prefills the draft — see [pm.antani.resentin.ui.chat.buildReplyPrefix]
 * for what each style actually expands to. Purely local, like [ChatDisplayMode]. */
enum class ReplyStyle {
    NICK,
    QUOTE,
    CUSTOM,
}

class AppPreferences(private val context: Context) {

    private val keyStayConnected = booleanPreferencesKey("stay_connected")
    private val keyChatDisplayMode = stringPreferencesKey("chat_display_mode")
    private val keyShowSeconds = booleanPreferencesKey("show_seconds")
    private val keyShowHostmaskInEvents = booleanPreferencesKey("show_hostmask_in_events")
    private val keyColoredNicklist = booleanPreferencesKey("colored_nicklist")
    private val keyReplyStyle = stringPreferencesKey("reply_style")
    private val keyReplyCustomTemplate = stringPreferencesKey("reply_custom_template")
    private val keyLastSyncedHost = stringPreferencesKey("last_synced_host")
    private val keyUnifiedPushEnabled = booleanPreferencesKey("unifiedpush_enabled")
    private val keyUnifiedPushEndpoint = stringPreferencesKey("unifiedpush_endpoint")
    private val keyUnifiedPushSubscriptionId = stringPreferencesKey("unifiedpush_subscription_id")
    private val keyPushDecryptionFailureAt = longPreferencesKey("push_decryption_failure_at")
    private val keyPinnedChannels = stringSetPreferencesKey("pinned_channels")
    private fun draftKey(networkSlug: String, channel: String) =
        stringPreferencesKey("draft_${channelKey(networkSlug, channel)}")
    private val keyUnreadFirst = booleanPreferencesKey("unread_first")
    private val keyFontScale = floatPreferencesKey("font_scale")

    val pinnedChannels: Flow<Set<String>> = context.dataStore.data.map { it[keyPinnedChannels] ?: emptySet() }

    /** Canonical network/target keys whose local draft is currently non-empty. */
    val chatDrafts: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences.asMap().mapNotNull { (key, value) ->
            key.name.removePrefix("draft_").takeIf {
                key.name.startsWith("draft_") && value is String && value.isNotEmpty()
            }
        }.toSet()
    }

    suspend fun setChannelPinned(networkSlug: String, channel: String, pinned: Boolean) {
        context.dataStore.edit {
            val current = it[keyPinnedChannels] ?: emptySet()
            it[keyPinnedChannels] = if (pinned) current + channelKey(networkSlug, channel) else current - channelKey(networkSlug, channel)
        }
    }

    /** Draft text is local-only and scoped to the canonical network/channel pair. */
    suspend fun getChatDraft(networkSlug: String, channel: String): String =
        context.dataStore.data.first()[draftKey(networkSlug, channel)].orEmpty()

    /** Empty drafts are removed so abandoned chats do not accumulate forever. */
    suspend fun setChatDraft(networkSlug: String, channel: String, draft: String) {
        context.dataStore.edit {
            val key = draftKey(networkSlug, channel)
            if (draft.isEmpty()) it.remove(key) else it[key] = draft
        }
    }

    /** Home ordering: unread chats float above the rest (pinned stay on top of those).
     * Off = pure server order (plus pinned). */
    val unreadFirst: Flow<Boolean> = context.dataStore.data.map { it[keyUnreadFirst] ?: false }

    suspend fun setUnreadFirst(value: Boolean) {
        context.dataStore.edit { it[keyUnreadFirst] = value }
    }

    /** Global text-size multiplier applied to the whole theme typography (see
     * ResentinTheme) — 1 = system default. */
    val fontScale: Flow<Float> = context.dataStore.data.map { it[keyFontScale] ?: 1f }

    suspend fun setFontScale(value: Float) {
        context.dataStore.edit { it[keyFontScale] = value }
    }

    val stayConnected: Flow<Boolean> = context.dataStore.data.map { it[keyStayConnected] ?: false }

    suspend fun setStayConnected(value: Boolean) {
        context.dataStore.edit { it[keyStayConnected] = value }
    }

    val chatDisplayMode: Flow<ChatDisplayMode> = context.dataStore.data.map {
        if (it[keyChatDisplayMode] == ChatDisplayMode.IRC_LINE.name) ChatDisplayMode.IRC_LINE else ChatDisplayMode.BUBBLES
    }

    suspend fun setChatDisplayMode(mode: ChatDisplayMode) {
        context.dataStore.edit { it[keyChatDisplayMode] = mode.name }
    }

    /** Applies to the timestamp shown on every message row, in both display modes. */
    val showSeconds: Flow<Boolean> = context.dataStore.data.map { it[keyShowSeconds] ?: false }

    suspend fun setShowSeconds(value: Boolean) {
        context.dataStore.edit { it[keyShowSeconds] = value }
    }

    /** Whether join/part/quit event lines show the sender's `[ident@host]` mask —
     * only present on the wire when the server actually captured it (see
     * `Grappa.Scrollback.Meta`'s `sender_user`/`sender_host`, absent for kick/mode/
     * nick_change, which carry no hostmask). Off by default: most people don't want
     * a hostname next to every join. */
    val showHostmaskInEvents: Flow<Boolean> = context.dataStore.data.map { it[keyShowHostmaskInEvents] ?: false }

    suspend fun setShowHostmaskInEvents(value: Boolean) {
        context.dataStore.edit { it[keyShowHostmaskInEvents] = value }
    }

    /** Local, fast-reading mirror of the server-persisted `DisplayPrefsDto.coloredNicklist`
     * (AppSettingsViewModel owns the actual read/write to the server; this is just so
     * ChatScreen/MemberListScreen can render off a synchronous local Flow instead of a
     * per-screen network round-trip). Kept in sync on every load and every toggle. */
    val coloredNicklist: Flow<Boolean> = context.dataStore.data.map { it[keyColoredNicklist] ?: false }

    suspend fun setColoredNicklist(value: Boolean) {
        context.dataStore.edit { it[keyColoredNicklist] = value }
    }

    val replyStyle: Flow<ReplyStyle> = context.dataStore.data.map {
        runCatching { ReplyStyle.valueOf(it[keyReplyStyle] ?: ReplyStyle.NICK.name) }.getOrDefault(ReplyStyle.NICK)
    }

    suspend fun setReplyStyle(style: ReplyStyle) {
        context.dataStore.edit { it[keyReplyStyle] = style.name }
    }

    /** Empty means "nothing saved yet" — callers fall back to a sensible default
     * rather than persisting one just to have something to show. */
    val replyCustomTemplate: Flow<String> = context.dataStore.data.map { it[keyReplyCustomTemplate] ?: "" }

    suspend fun setReplyCustomTemplate(template: String) {
        context.dataStore.edit { it[keyReplyCustomTemplate] = template }
    }

    /** The host every locally-cached table (networks/channels/messages/...) was last
     * populated from — every table is keyed by network *slug* alone, with no host
     * column, so switching to a different grappa server whose network happens to share
     * a slug (e.g. two servers both naming a network "azzurra") would otherwise silently
     * merge their data. Outlives sign-out (unlike [pm.antani.resentin.net.auth.TokenStore],
     * which the login screen needs cleared) so a host change is still detectable across
     * a sign-out/sign-in cycle. See [pm.antani.resentin.domain.repository.AuthRepository.signIn]. */
    val lastSyncedHost: Flow<String?> = context.dataStore.data.map { it[keyLastSyncedHost] }

    suspend fun setLastSyncedHost(host: String) {
        context.dataStore.edit { it[keyLastSyncedHost] = host }
    }

    /** User opt-in for battery-friendly notifications via a UnifiedPush distributor,
     * independent of [stayConnected] (the always-on WS fallback) — the two can both be
     * on at once, e.g. during the rollout of foreground-only WS sync. */
    val unifiedPushEnabled: Flow<Boolean> = context.dataStore.data.map { it[keyUnifiedPushEnabled] ?: false }

    suspend fun setUnifiedPushEnabled(value: Boolean) {
        context.dataStore.edit { it[keyUnifiedPushEnabled] = value }
    }

    /** The endpoint URL last handed to the server, so a distributor-initiated re-issue
     * (token rotation, distributor reinstall) can be sent to `POST /push/subscriptions`
     * as `supersedes`, letting the server prune the stale row atomically instead of
     * accumulating ghost subscriptions for the same device. */
    val unifiedPushEndpoint: Flow<String?> = context.dataStore.data.map { it[keyUnifiedPushEndpoint] }

    suspend fun setUnifiedPushEndpoint(endpoint: String?) {
        context.dataStore.edit {
            if (endpoint != null) it[keyUnifiedPushEndpoint] = endpoint else it.remove(keyUnifiedPushEndpoint)
        }
    }

    /** This device's own subscription id, so the Settings device list can highlight
     * "this device" among every subscription the account owns. */
    val unifiedPushSubscriptionId: Flow<String?> = context.dataStore.data.map { it[keyUnifiedPushSubscriptionId] }

    suspend fun setUnifiedPushSubscriptionId(id: String?) {
        context.dataStore.edit {
            if (id != null) it[keyUnifiedPushSubscriptionId] = id else it.remove(keyUnifiedPushSubscriptionId)
        }
    }

    /** Epoch millis of the last push this client received but could not decrypt — the
     * server-side `web_push_elixir` dependency currently emits the pre-RFC8291 "aesgcm"
     * draft, which no spec-compliant UnifiedPush distributor can decode (see
     * UnifiedPushService.onMessage). Surfaced as a quiet Settings note, not a
     * notification of its own, and cleared on the next successful decrypt so it reflects
     * whether this is CURRENTLY a problem rather than a permanent scar. */
    val pushDecryptionFailureAt: Flow<Long?> = context.dataStore.data.map { it[keyPushDecryptionFailureAt] }

    suspend fun setPushDecryptionFailureAt(epochMillis: Long?) {
        context.dataStore.edit {
            if (epochMillis != null) it[keyPushDecryptionFailureAt] = epochMillis else it.remove(keyPushDecryptionFailureAt)
        }
    }
}
