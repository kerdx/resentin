package pm.antani.resentin.net.dto

import org.junit.Assert.assertEquals
import org.junit.Test
import pm.antani.resentin.net.AppJson

class FeaturedChannelsDtoTest {
    @Test
    fun decodesServerPayloadWithDescriptionAndNullDescription() {
        val payload = AppJson.decodeFromString<FeaturedChannelsResponseDto>(
            """{"channels":[{"name":"#grappa","description":"Canale principale"},{"name":"#bar","description":null}]}""",
        )

        assertEquals(
            listOf(
                FeaturedChannelDto("#grappa", "Canale principale"),
                FeaturedChannelDto("#bar", null),
            ),
            payload.channels,
        )
    }

    @Test
    fun decodesEmptyServerPayload() {
        val payload = AppJson.decodeFromString<FeaturedChannelsResponseDto>("""{"channels":[]}""")

        assertEquals(emptyList<FeaturedChannelDto>(), payload.channels)
    }
}