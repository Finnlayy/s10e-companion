package dev.s10e.companion.speech

sealed interface VoiceIntent {
    object UnlockPhrase : VoiceIntent
    object EnterKidMode : VoiceIntent
    data class Free(val text: String) : VoiceIntent
    object Ignored : VoiceIntent
}

/** „agentenmodus“ fehlt im Wortschatz des Vosk-Modells, nur die getrennte Form ist erkennbar. */
val UNLOCK_GRAMMAR = listOf("agenten modus aktivieren", "[unk]")

fun classify(transcript: String, kidMode: Boolean): VoiceIntent {
    val t = transcript.trim().lowercase()
    if (t.isEmpty() || t == "[unk]") return VoiceIntent.Ignored

    val isUnlock = t == "agentenmodus aktivieren" || t == "agenten modus aktivieren"
    if (isUnlock) {
        return VoiceIntent.UnlockPhrase
    }

    if (kidMode) {
        return VoiceIntent.Ignored
    }

    if (t == "kinder modus aktivieren") {
        return VoiceIntent.EnterKidMode
    }

    return VoiceIntent.Free(t)
}
