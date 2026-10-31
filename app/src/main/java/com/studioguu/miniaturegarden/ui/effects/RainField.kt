package com.studioguu.miniaturegarden.ui.effects

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Which rain is falling: the weather's, or the blessed rain the player calls down. */
enum class RainStyle {
    /** Ordinary rain: quick, thin streaks, slanted by the wind. */
    RAIN,

    /** The blessed rain, called down with 雨乞い: slower, softer drops, warm in colour, a few of them glinting. */
    BLESSED,
}

/**
 * One drop as it is on screen right now: a streak from ([x], [y]) down to
 * ([x] - [slant] * [length], [y] + [length]), or a glint (a small bright point) when [glint].
 */
data class RainDrop(
    val x: Float,
    val y: Float,
    val length: Float,
    val slant: Float,
    val alpha: Float,
    val glint: Boolean = false,
)

/**
 * Where the rain is at a moment (#47).
 *
 * The old rain had every drop on a fixed lane at a fixed speed, all at once, forever: a sheet of
 * identical streaks sliding down. Here each drop is a slot that comes round at its own pace, and
 * each time it comes round it is a new drop: somewhere else across the screen, starting a little
 * higher or lower, falling faster or slower (a faster drop is a longer streak), fainter or
 * clearer, after a pause of its own. The wind's slant breathes slowly for all of them, and each
 * drop leans a little more or less than the rest.
 *
 * Everything is worked out from the time and the slot alone, so the rain at a given moment is the
 * same however often it is drawn (and can be tested), but nothing about it is regular.
 */
object RainField {

    /** How many drops can be in the air at once. */
    fun slots(style: RainStyle): Int = when (style) {
        RainStyle.RAIN -> 110
        RainStyle.BLESSED -> 70
    }

    /** How long a slot takes to come round, in seconds: its fall and its pause. */
    private fun period(style: RainStyle): Float = when (style) {
        RainStyle.RAIN -> 1.5f
        RainStyle.BLESSED -> 3.2f
    }

    /** The shortest and longest a drop takes to fall the height of the screen, in seconds. */
    fun fallRange(style: RainStyle): ClosedFloatingPointRange<Float> = when (style) {
        RainStyle.RAIN -> 0.55f..1.15f
        RainStyle.BLESSED -> 1.5f..2.6f
    }

    /** The drops in the air at [timeMillis] over a screen [width] by [height]. */
    fun drops(timeMillis: Long, width: Float, height: Float, style: RainStyle = RainStyle.RAIN): List<RainDrop> {
        val seconds = timeMillis / 1000f
        val period = period(style)
        val falls = fallRange(style)
        // The wind's slant, breathing slowly: the same for every drop at a moment.
        val gust = 0.2f + 0.08f * sin(seconds / 2.3f) + 0.04f * sin(seconds / 0.7f + 1.3f)
        val streak = height * if (style == RainStyle.RAIN) 0.028f else 0.014f
        val out = ArrayList<RainDrop>(slots(style))
        for (slot in 0 until slots(style)) {
            // Each slot starts at its own point in its round, so they never move together.
            val shifted = seconds + unit(slot, 0, 1) * period
            val round = floor(shifted / period).toInt()
            val local = shifted - round * period
            // This round's drop: how long it takes to fall the height of the screen, ...
            val fall = falls.start + (falls.endInclusive - falls.start) * unit(slot, round, 2)
            // ... where it comes into sight (some already mid-air) and where it ends (some stop
            // short, on leaves and roofs), ...
            val startY = height * (-0.15f + 0.45f * unit(slot, round, 5))
            val endY = height * (0.85f + 0.25f * unit(slot, round, 3))
            val travel = fall * (endY - startY) / height
            // ... and how long it waits before it falls.
            val wait = (period - travel).coerceAtLeast(0f) * unit(slot, round, 4)
            val t = (local - wait) / travel
            if (t < 0f || t > 1f) continue

            val lean = gust * (0.8f + 0.4f * unit(slot, round, 6))
            val fallen = (endY - startY) * t
            val sway = if (style == RainStyle.BLESSED) sin(t * 2f * PI.toFloat() + slot) * width * 0.006f else 0f
            // Placed so that it is over the screen half-way down its fall.
            val startX = -width * 0.05f + width * 1.1f * unit(slot, round, 7) + lean * (endY - startY) * 0.5f
            // Faster drops are longer streaks; each fades in and out at the ends of its fall.
            val speed = (falls.endInclusive / fall)
            val clarity = 0.25f + 0.5f * unit(slot, round, 8)
            val fade = sin(t * PI.toFloat()).coerceIn(0f, 1f).let { 0.35f + 0.65f * it }
            out += RainDrop(
                x = startX - fallen * lean + sway,
                y = startY + fallen,
                length = streak * (0.6f + 0.6f * speed) * (0.8f + 0.4f * unit(slot, round, 9)),
                slant = lean,
                alpha = (clarity * fade).coerceIn(0.05f, 0.85f),
                glint = style == RainStyle.BLESSED && unit(slot, round, 10) < 0.18f,
            )
        }
        return out
    }

    /** A number 0 until 1 for this slot, round and question: the same every time it is asked. */
    private fun unit(slot: Int, round: Int, salt: Int): Float {
        var h = slot * 73856093 xor (round * 19349663) xor (salt * 83492791)
        h = h xor (h ushr 13)
        h *= 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }
}
