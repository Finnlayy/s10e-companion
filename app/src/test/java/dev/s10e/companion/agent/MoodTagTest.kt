package dev.s10e.companion.agent

import dev.s10e.companion.eyes.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoodTagTest {

    @Test
    fun splitMoodTag_removesKnownTag() {
        assertEquals(TaggedReply(Mood.HAPPY, "Schön!"), splitMoodTag("[froh] Schön!"))
    }

    @Test
    fun splitMoodTag_ignoresCase() {
        assertEquals(Mood.SAD, splitMoodTag(" [Traurig] Schade.").mood)
    }

    @Test
    fun splitMoodTag_keepsTextWithoutTag() {
        assertEquals(TaggedReply(null, "Hallo"), splitMoodTag("Hallo"))
    }

    @Test
    fun splitMoodTag_keepsUnknownBrackets() {
        assertEquals(TaggedReply(null, "[Hinweis] Text"), splitMoodTag("[Hinweis] Text"))
    }

    @Test
    fun splitMoodTag_hidesTagWhileStreaming() {
        assertEquals("", splitMoodTag("[").text)
        assertEquals("", splitMoodTag("[begei").text)
    }

    @Test
    fun splitMoodTag_showsOtherBracketsWhileStreaming() {
        assertEquals("[Fehl", splitMoodTag("[Fehl").text)
        assertNull(splitMoodTag("[Fehl").mood)
    }
}
