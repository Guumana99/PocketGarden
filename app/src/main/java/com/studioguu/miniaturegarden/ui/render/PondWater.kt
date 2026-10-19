package com.studioguu.miniaturegarden.ui.render

import kotlin.math.PI
import kotlin.math.sin

/**
 * The pond's water moving, over its still picture (`pond.png`): light glinting here and there on
 * the surface, and now and then a ring spreading where something touched it. Plain data for a
 * moment of the garden's clock, placed on the open water inside the logs ([WATER_FROM] to
 * [WATER_TO] of the cell along both of the garden's axes); the garden draws it, clipped to that
 * water.
 *
 * Each pond has its own [seed] (its cell), so two ponds side by side do not glint in step.
 */
object PondWater {
    /** Where the open water starts and ends inside the logs, as a share of the cell along x and y. */
    const val WATER_FROM = 0.2f
    const val WATER_TO = 0.7f

    /** Points of light on the water at any one time. */
    const val GLINTS = 5

    /** How long one glint takes to brighten and fade. */
    const val GLINT_MILLIS = 2_600L

    /** Rings spreading over the water at any one time. */
    const val RIPPLES = 2

    /** How long one ring takes to spread out and fade away. */
    const val RIPPLE_MILLIS = 3_800L

    /** The widest a ring spreads, as a share of the water's width. */
    const val RIPPLE_REACH = 0.35f

    /**
     * A point of light at ([u], [v]) on the water (0 to 1 across it along x and y), [size] a share
     * of the water's width, [alpha] how bright it is now.
     */
    data class Glint(val u: Float, val v: Float, val size: Float, val alpha: Float)

    /** A ring centred on ([u], [v]), [radius] a share of the water's width, [alpha] how clear it is now. */
    data class Ripple(val u: Float, val v: Float, val radius: Float, val alpha: Float)

    data class Frame(val glints: List<Glint>, val ripples: List<Ripple>)

    /** The water of the pond with [seed] at [clockMillis]. */
    fun frameAt(clockMillis: Long, seed: Int): Frame {
        val glints = (0 until GLINTS).map { i ->
            // Each glint has its own place and its own moment; when it has faded it comes back
            // somewhere else.
            val offset = (hash(seed, i, 0) * GLINT_MILLIS).toLong()
            val t = clockMillis + offset
            val round = (t / GLINT_MILLIS).toInt()
            val phase = (t % GLINT_MILLIS).toFloat() / GLINT_MILLIS
            Glint(
                u = 0.12f + 0.76f * hash(seed, i, round * 2 + 1),
                v = 0.12f + 0.76f * hash(seed, i, round * 2 + 2),
                size = 0.035f + 0.03f * hash(seed, i, 7),
                alpha = sin(phase * PI).toFloat().let { it * it },
            )
        }
        val ripples = (0 until RIPPLES).map { i ->
            val offset = (hash(seed, i + 100, 0) * RIPPLE_MILLIS).toLong() + i * RIPPLE_MILLIS / RIPPLES
            val t = clockMillis + offset
            val round = (t / RIPPLE_MILLIS).toInt()
            val phase = (t % RIPPLE_MILLIS).toFloat() / RIPPLE_MILLIS
            Ripple(
                u = 0.25f + 0.5f * hash(seed, i + 100, round * 2 + 1),
                v = 0.25f + 0.5f * hash(seed, i + 100, round * 2 + 2),
                radius = RIPPLE_REACH * phase,
                alpha = (1f - phase) * 0.6f,
            )
        }
        return Frame(glints, ripples)
    }

    /** A number from 0 to 1 that depends only on its three arguments. */
    private fun hash(a: Int, b: Int, c: Int): Float {
        var h = a * 374_761_393 + b * 668_265_263 + c * 1_274_126_177
        h = (h xor (h ushr 13)) * 1_103_515_245
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65_535f
    }
}
