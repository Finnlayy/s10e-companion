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
    private var listening = false

    fun setGrammar(grammar: List<String>?) {
        lastGrammar = grammar
        listening = true
        rec.start(grammar)
    }

    fun stopListening() {
        listening = false
        rec.stop()
    }

    suspend fun say(text: String) {
        rec.stop()
        try {
            tts.speak(text)
        } finally {
            if (listening) rec.start(lastGrammar)
        }
    }
}
