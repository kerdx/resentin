package pm.antani.resentin.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.res.stringResource
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pm.antani.resentin.R

@RunWith(AndroidJUnit4::class)
class SlashCommandSuggestionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun slashShowsSuggestions() {
        setSuggestions("/")

        composeRule.onNodeWithTag("slash-command-suggestions").assertIsDisplayed()
        composeRule.onNodeWithTag("slash-command-me").assertIsDisplayed()
    }

    @Test
    fun slashWhFiltersToWhois() {
        setSuggestions("/wh")

        composeRule.onNodeWithTag("slash-command-whois").assertIsDisplayed()
        composeRule.onAllNodesWithTag("slash-command-join").assertCountEquals(0)
    }

    @Test
    fun selectingWhoisReturnsCanonicalCommand() {
        var selected: String? = null
        setSuggestions("/wh") { selected = it.name }

        composeRule.onNodeWithTag("slash-command-whois").performClick()

        assertEquals("whois", selected)
    }

    @Test
    fun normalTextHasNoSlashSuggestions() {
        setSuggestions("hello /")

        composeRule.onAllNodesWithTag("slash-command-suggestions").assertCountEquals(0)
    }

    @Test
    fun unsupportedCommandErrorIsRendered() {
        composeRule.setContent {
            MaterialTheme {
                UnsupportedCommandError()
            }
        }

        composeRule.onNodeWithTag("chat-error-snackbar").assertIsDisplayed()
    }

    private fun setSuggestions(
        input: String,
        onSelect: (SlashCommandSpec) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                SlashCommandSuggestions(
                    suggestions = suggestSlashCommands(input),
                    onSelect = onSelect,
                )
            }
        }
    }

    @Composable
    private fun UnsupportedCommandError() {
        ChatErrorSnackbar(stringResource(R.string.chat_slash_command_unsupported, "/me"))
    }
}