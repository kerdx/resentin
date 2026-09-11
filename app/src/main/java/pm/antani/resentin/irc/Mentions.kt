package pm.antani.resentin.irc

/** Plain case-insensitive substring match of [nick] in [body] — the same "did this
 * message mention me" signal NotificationRouter.shouldNotify() uses to decide whether
 * to notify, reused here (rather than reimplemented) so a message highlighted in chat
 * and a message that actually fired a notification can never drift apart. Word-boundary
 * matching, like the server's own `Grappa.Mentions.mentioned?/3`, would be more precise
 * but isn't implemented client-side yet — this mirrors the existing simpler behavior
 * instead of introducing a second, subtly different notion of "mention". */
fun containsMention(body: String, nick: String): Boolean = body.contains(nick, ignoreCase = true)

// mIRC control codes — bold, color (with its numeric parameters), reset, reverse,
// italic, underline, monospace — stripped before highlight matching, same as
// cicchetto's mentionMatch over mIRC-stripped text.
private val MIRC_STRIP_REGEX = Regex("\u0003(\\d{1,2}(,\\d{1,2})?)?|[\u0002\u000F\u0016\u001D\u001F\u0011]")

/** `/hilight` matching — own nick UNION watchlist patterns, each as a case-insensitive
 * word-boundary regex over mIRC-stripped text. Mirrors cicchetto's
 * `matchesWatchlist` (mentionMatch.ts), which the server mirrors in
 * `Grappa.Mentions.mentioned?/3` for push + sidebar counts. */
fun matchesHighlight(body: String, ownNick: String, patterns: List<String>): Boolean {
    val plain = MIRC_STRIP_REGEX.replace(body, "")
    return (listOf(ownNick) + patterns)
        .filter { it.isNotBlank() }
        .distinct()
        .any { term -> Regex("\\b${Regex.escape(term)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(plain) }
}
