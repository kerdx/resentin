package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** Per-subject notification preferences (#866 cluster B3) — five trigger booleans,
 * two channel/nick whitelists, and the `muted_targets` deny map. Wire keys match 1:1
 * (AppJson SnakeCase); unknown future keys are ignored on decode. */
@Serializable
data class NotificationPrefsDto(
    val channelMessagesAll: Boolean = false,
    val channelMessagesOnly: List<String> = emptyList(),
    val channelMentions: Boolean = false,
    val privateMessagesAll: Boolean = false,
    val privateMessagesOnly: List<String> = emptyList(),
    val presenceOnline: Boolean = false,
    val presenceOffline: Boolean = false,
    val mutedTargets: Map<String, MutedTargetDto> = emptyMap(),
)

/** One mute entry: `until` unix seconds, or explicit null = permanent. Decodes
 * leniently (absent key reads as permanent — the server always sends it). */
@Serializable
data class MutedTargetDto(
    val until: Long? = null,
)

@Serializable
data class NotificationPrefsEnvelopeDto(
    val notificationPrefs: NotificationPrefsDto,
)
