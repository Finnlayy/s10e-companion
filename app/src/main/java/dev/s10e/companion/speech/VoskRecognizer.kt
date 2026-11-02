package dev.s10e.companion.speech

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

class VoskRecognizer(private val model: Model) : dev.s10e.companion.speech.Recognizer {
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private val _results = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val results: Flow<String> = _results
    private val scope = CoroutineScope(Dispatchers.Default)

    @Synchronized
    override fun start(grammar: List<String>?) {
        stop()
        val next = if (grammar != null) {
            val grammarJson = grammar.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            Recognizer(model, 16000.0f, grammarJson)
        } else {
            Recognizer(model, 16000.0f)
        }
        try {
            speechService = SpeechService(next, 16000.0f)
        } catch (e: IOException) {
            Log.w("S10eCompanion", "Mikrofon nicht verfügbar: ${e.message}")
            next.close()
            return
        }
        recognizer = next

        speechService?.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {}
            override fun onResult(hypothesis: String?) {
                hypothesis?.let {
                    try {
                        val text = JSONObject(it).getString("text")
                        if (text.isNotBlank()) {
                            scope.launch { _results.emit(text) }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            override fun onFinalResult(hypothesis: String?) {
                hypothesis?.let {
                    try {
                        val text = JSONObject(it).getString("text")
                        if (text.isNotBlank()) {
                            scope.launch { _results.emit(text) }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            override fun onError(e: Exception?) {}
            override fun onTimeout() {}
        })
    }

    @Synchronized
    override fun stop() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        recognizer?.close()
        recognizer = null
    }
}
