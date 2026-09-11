package pm.antani.resentin.irc

/** Closed allowlist of IRC service nicks — mirrors cicchetto's `isServicesSender`
 * (servicesSender.ts), itself kept in lockstep with the server's
 * `Grappa.IRC.Identifier` services set. The server routes service NOTICE replies
 * to the `$server` pseudo-channel, so the compose path must know the same
 * predicate locally: a PRIVMSG to a service must NOT optimistically open a query
 * window (the reply will never arrive there). Channel-sigil targets are by
 * definition not services. */
private val SERVICE_NICKS = setOf(
    "nickserv",
    "chanserv",
    "memoserv",
    "operserv",
    "botserv",
    "hostserv",
    "helpserv",
    "rootserv",
    "seenserv",
    "statserv",
    "debugserv",
)

fun isServiceNick(target: String): Boolean {
    if (target.isEmpty()) return false
    if (target.first() in setOf('#', '&', '+', '!')) return false
    return target.lowercase() in SERVICE_NICKS
}

/** `/ns` -> `NickServ`, `/cs` -> `ChanServ`, ... — the two MemoServ spellings both
 * resolve, mirroring cicchetto's `parseServiceShortcut`. */
fun serviceNickFor(command: String): String? = when (command.lowercase()) {
    "ns" -> "NickServ"
    "cs" -> "ChanServ"
    "ms" -> "MemoServ"
    "os" -> "OperServ"
    "hs" -> "HelpServ"
    "rs" -> "RootServ"
    else -> null
}
