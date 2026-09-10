package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandTest {
    @Test
    fun parsesWhoisQueryAndJoinArguments() {
        assertParsed("/whois mario", "whois", listOf("mario"))
        assertParsed("/query mario", "query", listOf("mario"))
        assertParsed("/join #linux", "join", listOf("#linux"))
    }

    @Test
    fun acceptsOptionalAwayAndPartArguments() {
        assertParsed("/away", "away", emptyList())
        assertParsed("/part #linux", "part", listOf("#linux"))
    }

    @Test
    fun recognizesQueryAlias() {
        assertParsed("/q mario", "query", listOf("mario"))
    }

    @Test
    fun reportsUnknownIncompleteAndMissingArguments() {
        assertTrue(parseSlashCommand("/not-a-command") is SlashCommandParseResult.Invalid)
        assertTrue(parseSlashCommand("/") is SlashCommandParseResult.Incomplete)
        assertTrue(parseSlashCommand("/whois") is SlashCommandParseResult.Invalid)
    }

    @Test
    fun ignoresSlashInsideNormalText() {
        assertEquals(SlashCommandParseResult.NotACommand, parseSlashCommand("hello /whois mario"))
    }

    @Test
    fun filtersSuggestionsOnlyBeforeArguments() {
        assertEquals(listOf("whois"), suggestSlashCommands("/wh").map { it.name })
        assertEquals(7, suggestSlashCommands("/").size)
        assertTrue(suggestSlashCommands("/whois ").isEmpty())
        assertTrue(suggestSlashCommands("hello /").isEmpty())
        assertTrue(suggestSlashCommands("/not").isEmpty())
    }

    @Test
    fun completionAddsSpaceKeepsSuffixAndPlacesCursorBeforeSuffix() {
        val whois = slashCommandCatalog.first { it.name == "whois" }

        assertEquals(
            SlashCommandCompletion("/whois ", 7),
            completeSlashCommandInput("/wh", whois),
        )
        assertEquals(
            SlashCommandCompletion("/whois mario", 7),
            completeSlashCommandInput("/wh mario", whois),
        )
    }

    private fun assertParsed(input: String, name: String, arguments: List<String>) {
        val result = parseSlashCommand(input)
        assertTrue(result is SlashCommandParseResult.Parsed)
        result as SlashCommandParseResult.Parsed
        assertEquals(name, result.command.name)
        assertEquals(arguments, result.command.arguments)
    }
}