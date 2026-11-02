package dev.s10e.companion.speech

import kotlinx.coroutines.flow.Flow

interface Recognizer {
    fun start(grammar: List<String>?)
    fun stop()
    val results: Flow<String>
}

interface Speaker {
    suspend fun speak(text: String)
}

class SpeechController(
    private val rec: Recognizer,
    private val tts: Speaker
) {
    private var lastGrammar: List<String>? = null

    fun setGrammar(grammar: List<String>?) {
        lastGrammar = grammar
        rec.start(grammar)
    }

    suspend fun say(text: String) {
        rec.stop()
        try {
            tts.speak(text)
        } finally {
            rec.start(lastGrammar)
        }
    }
}
