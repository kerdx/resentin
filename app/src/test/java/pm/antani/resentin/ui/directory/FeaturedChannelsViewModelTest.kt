package pm.antani.resentin.ui.directory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.antani.resentin.net.dto.FeaturedChannelDto

class FeaturedChannelsViewModelTest {
    @Test
    fun successfulResponseReplacesFeaturedState() {
        val featured = listOf(FeaturedChannelDto("#grappa", "Canale principale"))

        val result = reduceFeaturedResult(
            state = DirectoryUiState(isFeaturedLoading = true, featuredError = "old error"),
            result = Result.success(featured),
            errorMessage = "unavailable",
        )

        assertEquals(featured, result.featured)
        assertFalse(result.isFeaturedLoading)
        assertEquals(null, result.featuredError)
    }

    @Test
    fun emptyResponseHidesFeaturedSectionData() {
        val result = reduceFeaturedResult(
            state = DirectoryUiState(
                featured = listOf(FeaturedChannelDto("#old")),
                isFeaturedLoading = true,
            ),
            result = Result.success(emptyList()),
            errorMessage = "unavailable",
        )

        assertTrue(result.featured.isEmpty())
        assertFalse(result.isFeaturedLoading)
        assertEquals(null, result.featuredError)
    }

    @Test
    fun failedResponseKeepsExistingDataAndExposesNonBlockingError() {
        val existing = listOf(FeaturedChannelDto("#grappa"))

        val result = reduceFeaturedResult(
            state = DirectoryUiState(featured = existing, isFeaturedLoading = true),
            result = Result.failure(IllegalStateException("offline")),
            errorMessage = "Featured channels unavailable",
        )

        assertEquals(existing, result.featured)
        assertFalse(result.isFeaturedLoading)
        assertEquals("Featured channels unavailable", result.featuredError)
    }

    @Test
    fun joinedChannelMatchingIsCaseInsensitiveAndCanonical() {
        assertTrue(isJoinedChannel("#GrApPa", setOf("#grappa")))
        assertFalse(isJoinedChannel("#other", setOf("#grappa")))
    }
}