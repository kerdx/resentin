package pm.antani.resentin.irc

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageLinesTest {
    @Test
    fun singleLineIsUnchanged() {
        assertEquals(listOf("ciao"), MessageLines.splitMessageLines("ciao"))
        assertEquals(listOf("  ciao  "), MessageLines.splitMessageLines("  ciao  "))
    }

    @Test
    fun splitsEveryLineEndingConvention() {
        assertEquals(
            listOf("uno", "due", "tre"),
            MessageLines.splitMessageLines("uno\r\ndue\rtre"), // CRLF, CR and LF mixed
        )
        assertEquals(listOf("uno", "due"), MessageLines.splitMessageLines("uno\ndue"))
    }

    @Test
    fun dropsBlankAndWhitespaceLines() {
        assertEquals(
            listOf("uno", "tre"),
            MessageLines.splitMessageLines("uno\n\n   \ntre"),
        )
    }

    @Test
    fun trailingNewlineYieldsNoExtraMessage() {
        assertEquals(listOf("uno"), MessageLines.splitMessageLines("uno\n"))
        assertEquals(listOf("uno", "due"), MessageLines.splitMessageLines("uno\r\n\r\ndue\n"))
    }
}