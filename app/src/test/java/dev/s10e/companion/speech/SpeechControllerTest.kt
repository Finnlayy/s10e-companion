package dev.s10e.companion.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechControllerTest {

    class FakeRecognizer : Recognizer {
        var startCount = 0
        var stopCount = 0
        override val results: Flow<String> = MutableSharedFlow()

        override fun start(grammar: List<String>?) {
            startCount++
        }

        override fun stop() {
            stopCount++
        }
    }

    class FakeSpeaker(private val throwOnSpeak: Boolean = false) : Speaker {
        var speakCount = 0
        override suspend fun speak(text: String) {
            if (throwOnSpeak) throw RuntimeException("Speak failed")
            speakCount++
        }
    }

    @Test
    fun say_pausesRecognizerAndResumes() = runTest {
        val rec = FakeRecognizer()
        val tts = FakeSpeaker()
        val controller = SpeechController(rec, tts)

        controller.say("test")

        assertEquals(1, rec.stopCount)
        assertEquals(1, tts.speakCount)
        assertEquals(1, rec.startCount)
    }

    @Test
    fun say_resumesRecognizerWhenTtsThrows() = runTest {
        val rec = FakeRecognizer()
        val tts = FakeSpeaker(throwOnSpeak = true)
        val controller = SpeechController(rec, tts)

        try {
            controller.say("test")
        } catch (e: Exception) {
            // expected
        }

        assertEquals(1, rec.stopCount)
        assertEquals(0, tts.speakCount)
        assertEquals(1, rec.startCount) // Even if it throws, it should resume
    }
}
