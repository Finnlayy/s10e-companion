package dev.s10e.companion.agent

import dev.s10e.companion.eyes.Mood

data class TaggedReply(val mood: Mood?, val text: String)

private val MOOD_WORDS = mapOf(
    "neutral" to Mood.NEUTRAL,
    "froh" to Mood.HAPPY,
    "traurig" to Mood.SAD,
    "überrascht" to Mood.SURPRISED,
    "verwirrt" to Mood.CONFUSED,
    "begeistert" to Mood.EXCITED,
    "liebevoll" to Mood.LOVE,
    "wütend" to Mood.ANGRY
)

val MOOD_TAG_PROMPT: String =
    "Beginne jede Antwort mit genau einem Wort in eckigen Klammern, das deine Stimmung zeigt: " +
        MOOD_WORDS.keys.joinToString(", ") { "[$it]" } + "."

/** Trennt die Stimmung vom Text. Im Strom bleibt ein angefangenes Wort verborgen, sonst stünde es kurz im Terminal. */
fun splitMoodTag(raw: String): TaggedReply {
    val start = raw.trimStart()
    if (!start.startsWith("[")) return TaggedReply(null, raw)

    val end = start.indexOf(']')
    if (end < 0) {
        val partial = start.drop(1).lowercase()
        return TaggedReply(null, if (MOOD_WORDS.keys.any { it.startsWith(partial) }) "" else raw)
    }
    val mood = MOOD_WORDS[start.substring(1, end).trim().lowercase()] ?: return TaggedReply(null, raw)
    return TaggedReply(mood, start.substring(end + 1).trimStart())
}
