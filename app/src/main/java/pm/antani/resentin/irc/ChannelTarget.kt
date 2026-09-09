package pm.antani.resentin.irc

private val CHANNEL_PREFIXES = setOf('#', '&', '+', '!')
private const val SERVER_PSEUDO_CHANNEL = "\$server"

/**
 * True for a query/DM target (a bare nick) — false for a real channel or the
 * "$server" pseudo-channel. RFC 2811's default CHANTYPES ("#&+!"); we don't track a
 * network's actual ISUPPORT CHANTYPES, so this is the closest safe default.
 */
fun isQueryTarget(channelOrNick: String): Boolean =
    channelOrNick != SERVER_PSEUDO_CHANNEL && channelOrNick.firstOrNull() !in CHANNEL_PREFIXES
/**
 * Canonical key used by grappa-irc for channel and query targets.
 *
 * The server folds ASCII A-Z when building persisted message keys and Phoenix
 * topics. Keeping this deliberately ASCII-only mirrors that behaviour without
 * changing non-ASCII nick characters unexpectedly.
 */
fun canonicalTarget(target: String): String =
    buildString(target.length) {
        target.forEach { character ->
            append(
                if (character in 'A'..'Z') {
                    (character.code + ('a'.code - 'A'.code)).toChar()
                } else {
                    character
                },
            )
        }
    }
