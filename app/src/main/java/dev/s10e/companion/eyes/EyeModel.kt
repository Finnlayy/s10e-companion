package dev.s10e.companion.eyes

import kotlin.math.sin

enum class Mood { NEUTRAL, HAPPY, ANGRY, LISTENING, THINKING, DIZZY, SLEEPY, SAD, SURPRISED, CONFUSED, EXCITED, LOVE }

/** Kurze Gefühle, die von selbst wieder zu NEUTRAL werden. */
val FLEETING_MOODS = setOf(Mood.HAPPY, Mood.ANGRY, Mood.SAD, Mood.SURPRISED, Mood.CONFUSED, Mood.EXCITED, Mood.LOVE)

data class EyeShape(
    val width: Float,
    val height: Float,
    val corner: Float,
    val rotationDeg: Float,
    val argb: Long,
    val heart: Boolean = false
)

data class EyeFrame(val left: EyeShape, val right: EyeShape, val offsetX: Float, val offsetY: Float)

fun eyeFrame(mood: Mood, timeNanos: Long, lookX: Float = 0f, lookY: Float = 0f, pose: IdlePose = IdlePose()): EyeFrame {
    // Auge 108×148 dp, Ecke 40 dp, Abstand 58 dp
    val defaultWidth = 108f
    val defaultHeight = 148f
    var cornerL = 40f
    var cornerR = 40f

    var heightL = defaultHeight
    var heightR = defaultHeight
    var widthL = defaultWidth
    var widthR = defaultWidth
    var rotL = 0f
    var rotR = 0f
    var color = 0xFF00F0FF // NEUTRAL
    var heart = false
    val tSec = timeNanos / 1_000_000_000.0

    when (mood) {
        Mood.NEUTRAL -> {
            color = 0xFF00F0FF
        }
        Mood.HAPPY -> {
            color = 0xFF00FF88
            heightL = 44f
            heightR = 44f
        }
        Mood.ANGRY -> {
            color = 0xFFFF2A4B
            rotL = 24f
            rotR = -24f
            heightL = defaultHeight * 0.7f
            heightR = defaultHeight * 0.7f
        }
        Mood.LISTENING -> {
            color = 0xFFB84DFF
            widthL = defaultWidth * 1.1f
            widthR = defaultWidth * 1.1f
            heightL = defaultHeight * 1.1f
            heightR = defaultHeight * 1.1f
        }
        Mood.THINKING -> {
            color = 0xFFFFAA00
            heightL = 26f
            heightR = 26f
        }
        Mood.DIZZY -> {
            color = 0xFFFFE600
            widthL = 95f
            heightL = 95f
            widthR = 75f
            heightR = 75f
            cornerL = 30f
            cornerR = 25f
            val spinNanos = 350_000_000L
            rotL = (timeNanos % spinNanos) * 360f / spinNanos
            rotR = -rotL
        }
        Mood.SLEEPY -> {
            color = 0xFF005577
            heightL = 10f
            heightR = 10f
        }
        Mood.SAD -> {
            color = 0xFF4D7CFF
            rotL = -16f
            rotR = 16f
            heightL = defaultHeight * 0.75f
            heightR = defaultHeight * 0.75f
        }
        Mood.SURPRISED -> {
            color = 0xFFE8FBFF
            widthL = 96f
            widthR = 96f
            heightL = 176f
            heightR = 176f
            cornerL = 48f
            cornerR = 48f
        }
        Mood.CONFUSED -> {
            color = 0xFFFFC14D
            heightL = 150f
            heightR = 62f
            rotR = 12f
        }
        Mood.EXCITED -> {
            color = 0xFFFF66CC
            val pulse = 1f + 0.08f * sin(tSec * 18).toFloat()
            widthL = defaultWidth * pulse
            widthR = defaultWidth * pulse
            heightL = defaultHeight * pulse
            heightR = defaultHeight * pulse
        }
        Mood.LOVE -> {
            color = 0xFFFF4D8D
            heart = true
            val pulse = 1f + 0.08f * sin(tSec * 7).toFloat()
            widthL = 124f * pulse
            widthR = 124f * pulse
            heightL = 116f * pulse
            heightR = 116f * pulse
        }
    }

    val blinkCycle = 4_000_000_000L
    val blinkDuration = 130_000_000L
    val timeInCycle = timeNanos % blinkCycle
    if (mood == Mood.NEUTRAL && timeInCycle < blinkDuration) {
        val mid = blinkDuration / 2
        val progress = if (timeInCycle < mid) {
            timeInCycle.toFloat() / mid
        } else {
            1f - (timeInCycle - mid).toFloat() / mid
        }
        heightL -= (heightL - 4f) * progress
        heightR -= (heightR - 4f) * progress
    }

    // Leerlauf-Gesten gelten nur im Ruhezustand, sonst störten sie ein gezeigtes Gefühl.
    val idle = if (mood == Mood.NEUTRAL) pose else IdlePose()
    heightL = (heightL * idle.leftOpen).coerceAtLeast(4f)
    heightR = (heightR * idle.rightOpen).coerceAtLeast(4f)

    val driftX = (sin(tSec * 0.1) * 4f).toFloat()
    val driftY = (sin(tSec * 0.13) * 4f).toFloat()

    val finalOffsetX = lookX + driftX + idle.glanceX
    val finalOffsetY = lookY + driftY

    val left = EyeShape(widthL, heightL, cornerL, rotL, color, heart)
    val right = EyeShape(widthR, heightR, cornerR, rotR, color, heart)

    return EyeFrame(left, right, finalOffsetX, finalOffsetY)
}
