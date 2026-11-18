package com.eyeguard.android

import android.graphics.Color
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object ColorMath {
    fun kelvinToRgb(kelvin: Int): Triple<Int, Int, Int> {
        val clamped = kelvin.coerceIn(1000, 6500)
        val temp = clamped / 100.0

        val red: Double
        val green: Double
        val blue: Double

        if (temp <= 66.0) {
            red = 255.0
            green = 99.4708025861 * ln(temp) - 161.1195681661
            blue = if (temp <= 19.0) {
                0.0
            } else {
                138.5177312231 * ln(temp - 10.0) - 305.0447927307
            }
        } else {
            red = 329.698727446 * (temp - 60.0).pow(-0.1332047592)
            green = 288.1221695283 * (temp - 60.0).pow(-0.0755148492)
            blue = 255.0
        }

        return Triple(
            max(0, min(255, red.roundToInt())),
            max(0, min(255, green.roundToInt())),
            max(0, min(255, blue.roundToInt()))
        )
    }

    fun blueReductionPercent(kelvin: Int, paused: Boolean): Int {
        if (paused || kelvin >= 6500) return 0
        val (_, _, b) = kelvinToRgb(kelvin)
        return (((255.0 - b) / 255.0) * 100.0).roundToInt().coerceIn(0, 100)
    }

    fun computeOverlayArgb(kelvin: Int, brightness: Int, paused: Boolean): Int {
        if (paused) return Color.TRANSPARENT

        val (r, g, b) = kelvinToRgb(kelvin)
        val warmthStrength = ((6500 - kelvin).coerceAtLeast(0)) / (6500f - 1200f)
        val warmAlpha = (warmthStrength * 110f).roundToInt().coerceIn(0, 150)

        val dimStrength = (100 - brightness.coerceIn(20, 100)) / 100f
        val dimAlpha = (dimStrength * 175f).roundToInt().coerceIn(0, 190)

        // Blend warm amber tint with black extra-dim attenuation in a single overlay pass
        val combinedAlpha = max(warmAlpha, dimAlpha).coerceIn(0, 210)
        if (combinedAlpha == 0) return Color.TRANSPARENT

        val warmWeight = warmAlpha.toFloat() / (warmAlpha + dimAlpha + 1f)
        val outR = (r * warmWeight).roundToInt().coerceIn(0, 255)
        val outG = (g * 0.78f * warmWeight).roundToInt().coerceIn(0, 255)
        val outB = (b * 0.25f * warmWeight).roundToInt().coerceIn(0, 255)

        return Color.argb(combinedAlpha, outR, outG, outB)
    }
}
