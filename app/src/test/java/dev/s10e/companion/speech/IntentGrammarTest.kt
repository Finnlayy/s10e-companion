package dev.s10e.companion.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentGrammarTest {
    @Test
    fun classify_kidMode_onlyUnlockPhraseCounts() {
        assertEquals(VoiceIntent.UnlockPhrase, classify("agentenmodus aktivieren", true))
        assertEquals(VoiceIntent.Ignored, classify("hallo welt", true))
    }

    @Test
    fun classify_unlock_matchesSeparatedWords() {
        assertEquals(VoiceIntent.UnlockPhrase, classify("agenten modus aktivieren", false))
        assertEquals(VoiceIntent.UnlockPhrase, classify("agenten modus aktivieren", true))
        assertEquals(VoiceIntent.UnlockPhrase, classify("agentenmodus aktivieren", false))
    }

    @Test
    fun classify_kidModeCommand_onlyOutsideKidMode() {
        assertEquals(VoiceIntent.EnterKidMode, classify("kinder modus aktivieren", false))
        assertEquals(VoiceIntent.Ignored, classify("kinder modus aktivieren", true))
    }

    @Test
    fun classify_unk_isIgnored() {
        assertEquals(VoiceIntent.Ignored, classify("[unk]", false))
        assertEquals(VoiceIntent.Ignored, classify("[unk]", true))
        assertEquals(VoiceIntent.Ignored, classify(" [unk] ", false))
    }
}
