package pm.antani.resentin.irc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightMatchTest {
    @Test
    fun matchesOwnNickAndPatterns() {
        assertTrue(matchesHighlight("ciao mario, come va?", "mario", emptyList()))
        assertTrue(matchesHighlight("release urgente in produzione", "mario", listOf("urgente")))
        assertFalse(matchesHighlight("tutto tranquillo", "mario", listOf("urgente")))
    }

    @Test
    fun requiresWordBoundaries() {
        assertFalse(matchesHighlight("marioscopio rotto", "mario", emptyList()))
        assertFalse(matchesHighlight("tutto urgentissimo", "mario", listOf("urgente")))
        assertTrue(matchesHighlight("MARIO vieni?", "mario", emptyList()))
    }

    @Test
    fun stripsMircCodesBeforeMatching() {
        assertTrue(matchesHighlight("\u0002mario\u0002 vieni?", "mario", emptyList()))
        assertTrue(matchesHighlight("\u000304urgente\u0003 da fare", "mario", listOf("urgente")))
    }

    @Test
    fun ignoresBlankPatterns() {
        assertFalse(matchesHighlight("tutto tranquillo", "mario", listOf("", "  ")))
    }

    @Test
    fun classifiesServiceNicks() {
        assertTrue(isServiceNick("NickServ"))
        assertTrue(isServiceNick("chanserv"))
        assertFalse(isServiceNick("#canale"))
        assertFalse(isServiceNick("mario"))
        assertFalse(isServiceNick(""))
    }
}
