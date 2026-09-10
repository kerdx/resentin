package pm.antani.resentin.irc

import pm.antani.resentin.data.db.MessageEntity

/** The server-backed per-channel display preference used by grappa-irc/cicchetto. */
const val LARGE_CHANNEL_THRESHOLD = 200

private val SUPPRESSED_PRESENCE_KINDS = setOf(
    "join",
    "part",
    "quit",
    "nick_change",
    "mode",
)

/** Same opaque key as cicchetto and grappa-irc: `${network} ${canonical channel}`. */
fun presenceFilterKey(networkSlug: String, channelName: String): String =
    "$networkSlug ${canonicalTarget(channelName)}"

fun isSuppressedPresenceKind(kind: String): Boolean = kind in SUPPRESSED_PRESENCE_KINDS

/** Explicit preference wins; an unset preference follows the server's size default. */
fun presenceVisible(pref: String?, memberCount: Int): Boolean = when (pref) {
    "hide" -> false
    "show" -> true
    else -> memberCount < LARGE_CHANNEL_THRESHOLD
}

/** Hide peer presence only. Own presence is load-bearing for window/read state. */
fun isMessageVisibleUnderPresenceFilter(
    message: MessageEntity,
    visible: Boolean,
    ownNick: String?,
): Boolean = visible ||
    !isSuppressedPresenceKind(message.kind) ||
    (ownNick != null && message.sender.equals(ownNick, ignoreCase = true))
