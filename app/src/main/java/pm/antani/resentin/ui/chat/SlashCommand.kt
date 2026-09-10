package pm.antani.resentin.ui.chat

/** A slash command known by the client, including aliases accepted by the parser. */
data class SlashCommandSpec(
    val name: String,
    val aliases: Set<String> = emptySet(),
    val requiresArgument: Boolean = false,
)

/** A parsed command with its canonical name and whitespace-separated arguments. */
data class SlashCommand(
    val spec: SlashCommandSpec,
    val arguments: List<String>,
) {
    val name: String get() = spec.name
}

enum class SlashCommandError {
    UnknownCommand,
    MissingArgument,
    InvalidArgument,
}

sealed interface SlashCommandParseResult {
    data object NotACommand : SlashCommandParseResult
    data object Incomplete : SlashCommandParseResult
    data class Parsed(val command: SlashCommand) : SlashCommandParseResult
    data class Invalid(val error: SlashCommandError, val token: String) : SlashCommandParseResult
}

val slashCommandCatalog: List<SlashCommandSpec> = listOf(
    SlashCommandSpec("me", requiresArgument = true),
    SlashCommandSpec("join", requiresArgument = true),
    SlashCommandSpec("part"),
    SlashCommandSpec("whois", requiresArgument = true),
    SlashCommandSpec("query", aliases = setOf("q"), requiresArgument = true),
    SlashCommandSpec("away"),
    SlashCommandSpec("reconnect"),
)

fun parseSlashCommand(input: String): SlashCommandParseResult {
    if (!input.startsWith('/')) return SlashCommandParseResult.NotACommand
    val body = input.trim().removePrefix("/").trim()
    if (body.isEmpty()) return SlashCommandParseResult.Incomplete

    val tokens = body.split(Regex("\\s+"))
    val token = tokens.first()
    val spec = slashCommandCatalog.firstOrNull { command ->
        command.name.equals(token, ignoreCase = true) || command.aliases.any { it.equals(token, ignoreCase = true) }
    } ?: return SlashCommandParseResult.Invalid(SlashCommandError.UnknownCommand, token)

    val arguments = tokens.drop(1)
    if (spec.requiresArgument && arguments.isEmpty()) {
        return SlashCommandParseResult.Invalid(SlashCommandError.MissingArgument, spec.name)
    }
    if (spec.name == "join" && arguments.firstOrNull()?.firstOrNull() !in setOf('#', '&', '+', '!')) {
        return SlashCommandParseResult.Invalid(SlashCommandError.InvalidArgument, spec.name)
    }
    return SlashCommandParseResult.Parsed(SlashCommand(spec, arguments))
}

/** Returns suggestions only while the complete input is still the slash-command token. */
fun suggestSlashCommands(input: String): List<SlashCommandSpec> {
    if (!input.startsWith('/')) return emptyList()
    val query = input.removePrefix("/")
    if (query.any(Char::isWhitespace)) return emptyList()
    return slashCommandCatalog.filter { command ->
        command.name.startsWith(query, ignoreCase = true) ||
            command.aliases.any { it.startsWith(query, ignoreCase = true) }
    }
}

data class SlashCommandCompletion(
    val text: String,
    val cursor: Int,
)

/** Replaces the current slash token, keeps text after it, and places the caret before that text. */
fun completeSlashCommandInput(input: String, command: SlashCommandSpec): SlashCommandCompletion {
    val tokenEnd = input.indexOfFirst(Char::isWhitespace).let { if (it < 0) input.length else it }
    val suffix = input.substring(tokenEnd).trimStart()
    val inserted = "/${command.name} "
    return SlashCommandCompletion(text = inserted + suffix, cursor = inserted.length)
}