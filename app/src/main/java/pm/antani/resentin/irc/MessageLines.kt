package pm.antani.resentin.irc

/**
 * Mirror of cicchetto's `lib/messageLines.ts`: the ONE definition of how a
 * free-text body becomes IRC frames. A PRIVMSG body cannot carry an embedded
 * LF/CR (the server rejects it), so a multi-line body is the operator asking
 * for one message per line. The flood-guard threshold and the send fan-out
 * share this splitter so the count we show is the count we send.
 */
object MessageLines {

    /** Splits on every line-ending convention — CRLF, lone CR, LF — because
     * both CR and LF are forbidden on the wire. Drops lines that are blank
     * after trimming: an empty PRIVMSG is invalid, and a blank line between
     * paragraphs is not worth a frame. */
    private val LINE_SEPARATOR = Regex("\r\n|\r|\n")

    fun splitMessageLines(body: String): List<String> =
        body.split(LINE_SEPARATOR).filter { it.trim().isNotEmpty() }
}