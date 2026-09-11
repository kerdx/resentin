package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import pm.antani.resentin.data.db.MessageEntity

class ChatSearchTest {
    @Test
    fun matchesMessageBodyAndSenderCaseInsensitively() {
        val messages = listOf(
            MessageEntity("libera", "alice", 1, 0, "privmsg", "Bob", "Ciao a tutti"),
            MessageEntity("libera", "alice", 2, 0, "privmsg", "Carla", "Tutto bene"),
            MessageEntity("libera", "alice", 3, 0, "privmsg", "Bob", "A dopo"),
        )

        assertEquals(listOf(1L, 3L), findLocalChatMatches(messages, "BOB").map { it.id })
        assertEquals(listOf(1L), findLocalChatMatches(messages, "tutti").map { it.id })
        assertEquals(emptyList<Long>(), findLocalChatMatches(messages, "   ").map { it.id })
    }
}
