package pm.antani.resentin.ui.directory

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pm.antani.resentin.net.dto.FeaturedChannelDto

@RunWith(AndroidJUnit4::class)
class DirectoryContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun featuredSectionIsVisibleWhenServerReturnsChannels() {
        setContent(featured = listOf(FeaturedChannelDto("#grappa", "Main channel")))

        composeRule.onNodeWithTag("directory-featured-section").assertIsDisplayed()
    }

    @Test
    fun featuredSectionIsHiddenWhenServerReturnsEmptyList() {
        setContent()

        composeRule.onAllNodesWithTag("directory-featured-section").assertCountEquals(0)
    }

    @Test
    fun tappingJoinCallsChannelCallback() {
        var clicked: String? = null
        setContent(featured = listOf(FeaturedChannelDto("#grappa"))) { clicked = it }

        composeRule.onNodeWithTag("directory-featured-action").performClick()

        assert(clicked == "#grappa")
    }

    @Test
    fun tappingJoinedChannelCallsSameCallbackToOpenChat() {
        var clicked: String? = null
        setContent(
            featured = listOf(FeaturedChannelDto("#grappa")),
            joinedChannels = setOf("#grappa"),
        ) { clicked = it }

        composeRule.onNodeWithTag("directory-featured-action").performClick()

        assert(clicked == "#grappa")
    }

    private fun setContent(
        featured: List<FeaturedChannelDto> = emptyList(),
        joinedChannels: Set<String> = emptySet(),
        onClick: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                DirectoryContent(
                    state = DirectoryUiState(featured = featured, joinedChannels = joinedChannels),
                    onChannelClick = onClick,
                    onLoadMore = {},
                )
            }
        }
    }
}