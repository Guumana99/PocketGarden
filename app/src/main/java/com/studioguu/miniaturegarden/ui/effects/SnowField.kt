package com.studioguu.miniaturegarden.ui.effects

import kotlin.math.PI
import kotlin.math.sin

/**
 * Where every snowflake is at a given moment. Like [RainField], it is worked out from the clock
 * alone, so nothing is remembered from frame to frame.
 */
object SnowField {
    /** How many flakes are in the air at once. */
    const val FLAKES = 70

    /** A flake on screen: where, how large (in dp) and how clear. */
    data class Flake(val x: Float, val y: Float, val radius: Float, val alpha: Float)

    fun flakes(timeMillis: Long, width: Float, height: Float): List<Flake> = List(FLAKES) { i ->
        val seed = scramble(i)
        fun part(shift: Int) = ((seed ushr shift) and 0xFFFF) / 65535f
        // Smaller flakes are further off: slower, and fainter.
        val near = part(0)
        val fallMillis = 9_000f + (1f - near) * 7_000f
        val phase = part(16)
        val t = ((timeMillis / fallMillis + phase) % 1f)
        val drift = sin((timeMillis / (2_600f + part(32) * 1_800f) + phase * 6f) * 2f * PI.toFloat())
        Flake(
            x = part(48) * width + drift * (6f + near * 10f),
            y = -10f + t * (height + 20f),
            radius = 1.2f + near * 1.8f,
            alpha = 0.45f + near * 0.45f,
        )
    }

    private fun scramble(i: Int): Long {
        var h = (i + 1) * -7046029254386353131L
        h = (h xor (h ushr 32)) * -4417276706812531889L
        return h xor (h ushr 29)
    }
}
