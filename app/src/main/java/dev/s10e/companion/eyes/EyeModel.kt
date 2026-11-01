package dev.s10e.companion.eyes

import kotlin.math.sin

enum class Mood { NEUTRAL, HAPPY, ANGRY, LISTENING, THINKING, DIZZY, SLEEPY }

data class EyeShape(val width: Float, val height: Float, val corner: Float, val rotationDeg: Float, val argb: Long)

data class EyeFrame(val left: EyeShape, val right: EyeShape, val offsetX: Float, val offsetY: Float)

fun eyeFrame(mood: Mood, timeNanos: Long, lookX: Float = 0f, lookY: Float = 0f): EyeFrame {
    val defaultWidth = 108f
    val defaultHeight = 148f
    val corner = 40f

    var heightL = defaultHeight
    var heightR = defaultHeight
    var widthL = defaultWidth
    var widthR = defaultWidth
    var rotL = 0f
    var rotR = 0f
    var color = 0xFF00F0FF

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
            heightL = 75f
            widthR = 95f
            heightR = 75f
            rotL = ((timeNanos / 10_000_000L) % 360L).toFloat()
            rotR = rotL
        }
        Mood.SLEEPY -> {
            color = 0xFF005577
            heightL = 10f
            heightR = 10f
        }
    }

    val blinkCycle = 4_000_000_000L
    val blinkDuration = 130_000_000L
    val timeInCycle = timeNanos % blinkCycle
    if (timeInCycle < blinkDuration) {
        val mid = blinkDuration / 2
        val progress = if (timeInCycle < mid) {
            timeInCycle.toFloat() / mid
        } else {
            1f - (timeInCycle - mid).toFloat() / mid
        }
        heightL -= (heightL - 4f) * progress
        heightR -= (heightR - 4f) * progress
    }

    val tSec = timeNanos / 1_000_000_000.0
    val driftX = (sin(tSec * 0.1) * 4f).toFloat()
    val driftY = (sin(tSec * 0.13) * 4f).toFloat()

    val finalOffsetX = lookX + driftX
    val finalOffsetY = lookY + driftY

    val left = EyeShape(widthL, heightL, corner, rotL, color)
    val right = EyeShape(widthR, heightR, corner, rotR, color)

    return EyeFrame(left, right, finalOffsetX, finalOffsetY)
}
