package dev.s10e.companion.speech

sealed interface VoiceIntent {
    object UnlockPhrase : VoiceIntent
    object EnterKidMode : VoiceIntent
    data class Free(val text: String) : VoiceIntent
    object Ignored : VoiceIntent
}

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

    // Enter kid mode isn't explicitly defined in phase 5 grammar rules,
    // but the sealed class asks for it. I'll leave it out of classification
    // for now and handle Free text.

    return VoiceIntent.Free(t)
}
