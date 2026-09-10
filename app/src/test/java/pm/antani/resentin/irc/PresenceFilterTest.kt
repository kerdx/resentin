package pm.antani.resentin.irc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.antani.resentin.data.db.MessageEntity

class PresenceFilterTest {
    @Test
    fun `presence key matches cicchetto canonical channel key`() {
        assertEquals("azzurra #grappa", presenceFilterKey("azzurra", "#GRAPPA"))
    }

    @Test
    fun `explicit hide wins regardless of member count`() {
        assertFalse(presenceVisible("hide", 1))
        assertFalse(presenceVisible("hide", 500))
        assertTrue(presenceVisible("show", 500))
    }

    @Test
    fun `unset follows the large channel default`() {
        assertTrue(presenceVisible(null, LARGE_CHANNEL_THRESHOLD - 1))
        assertFalse(presenceVisible(null, LARGE_CHANNEL_THRESHOLD))
    }

    @Test
    fun `hidden presence keeps own rows but drops peer rows`() {
        val own = MessageEntity("net", "#chan", 1, 1L, "join", "Me", null, "{}")
        val peer = own.copy(id = 2, sender = "Someone")
        assertTrue(isMessageVisibleUnderPresenceFilter(own, false, "me"))
        assertFalse(isMessageVisibleUnderPresenceFilter(peer, false, "me"))
        assertTrue(isMessageVisibleUnderPresenceFilter(peer.copy(kind = "privmsg"), false, "me"))
    }
}
