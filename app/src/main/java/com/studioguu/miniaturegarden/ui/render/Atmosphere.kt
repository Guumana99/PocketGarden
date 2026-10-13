package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.WeatherType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The light that things in the garden cast their shadows in.
 *
 * A point that stands `h` above the ground throws its shadow `h * dx` to the right and `h * dy`
 * towards the viewer of the point below it (both in the same units as `h`, on screen).
 *
 * @property alpha how dark the shadow is, 0 (none) to 1 (black).
 */
data class SunLight(val dx: Float, val dy: Float, val alpha: Float) {
    /** How long the shadow of something one unit tall is. */
    val length: Float get() = kotlin.math.hypot(dx, dy)
}

/**
 * Where the light comes from at each time of day, and how strong it is in each kind of weather.
 *
 * The sun rises on the right of the screen and sets on the left: shadows are long and point left
 * in the morning, shrink towards noon, and grow out to the right in the evening. After dark a
 * faint moon keeps things from looking pasted onto the ground. Clouds soften the shadows and rain
 * all but removes them.
 */
object Daylight {
    const val SUNRISE_HOUR = 5f
    const val SUNSET_HOUR = 19f

    private const val NOON_LENGTH = 0.35f
    private const val HORIZON_LENGTH = 1.6f

    /** @param hourOfDay local time, 0 until 24, with the minutes as a fraction. */
    fun at(hourOfDay: Float, weather: WeatherType): SunLight {
        val strength = shadowStrength(weather)
        val hour = ((hourOfDay % 24f) + 24f) % 24f
        if (hour < SUNRISE_HOUR || hour >= SUNSET_HOUR) {
            // Moonlight: always from the same side, and weak.
            return SunLight(dx = 0.45f, dy = 0.3f, alpha = strength * 0.35f)
        }
        // 0 at sunrise, 1 at sunset.
        val day = (hour - SUNRISE_HOUR) / (SUNSET_HOUR - SUNRISE_HOUR)
        val height = sin(PI * day).toFloat()        // of the sun: 0 on the horizon, 1 at noon
        val side = cos(PI * day).toFloat()          // 1 in the east, 0 overhead, -1 in the west
        val length = NOON_LENGTH + (HORIZON_LENGTH - NOON_LENGTH) * (1f - height)
        return SunLight(
            dx = -side * length,
            // Always a little towards the viewer, so that a shadow never hides behind its owner.
            dy = length * (0.15f + 0.45f * height),
            // Low sun is weak sun.
            alpha = strength * (0.55f + 0.45f * height),
        )
    }

    /** How dark shadows are at best under [weather]. */
    fun shadowStrength(weather: WeatherType): Float = when (weather) {
        WeatherType.SUNNY -> 0.30f
        WeatherType.CLOUDY -> 0.14f
        WeatherType.RAINY -> 0.07f
    }
}

/** Which way a gust crosses the garden. Either way it runs diagonally across the screen. */
enum class GustAxis {
    /** From the west corner to the east: down and to the right on screen. */
    ALONG_X,

    /** From the north corner to the south: down and to the left on screen. */
    ALONG_Y,
}

/**
 * A gust on its way across the garden.
 *
 * @property position how far its front has come along [axis], in grid coordinates. It starts
 *   before the garden's edge and ends beyond the far one.
 * @property strength how hard it blows, see [Wind.strength].
 */
data class GustFront(val axis: GustAxis, val position: Float, val strength: Float)

/**
 * The wind that moves the plants. It is a pure function of time and place, so nothing has to be
 * remembered between frames and every plant can be asked where it is leaning right now.
 *
 * Most of the time the air is almost still: a breeze in which things barely stir. Every so often,
 * at irregular intervals, a gust sweeps across the garden from one corner to the other. As its
 * front passes a plant, the plant bows with it, shakes, and over the next second or so settles
 * back into the breeze. Since the front takes time to cross, the plants bow one after another,
 * and the gust is seen running over the garden.
 */
object Wind {
    /** The share of a full lean that the breeze alone accounts for; the rest is gusts. */
    const val CALM_SHARE = 0.18f

    /** One slow swing of the breeze takes about this long. */
    private const val BREEZE_MILLIS = 3400.0

    /** Time is cut into slots of this length; each slot has one gust, somewhere inside it. */
    const val GUST_SLOT_MILLIS = 10_000L

    /** A gust starts at most this long after the beginning of its slot. */
    private const val GUST_START_SPREAD_MILLIS = 6_000L

    /** How long a gust's front takes to cross the garden, at its quickest and at its slowest. */
    private const val GUST_CROSS_MIN_MILLIS = 1_300L
    private const val GUST_CROSS_MAX_MILLIS = 2_100L

    /** The front starts this far before the garden's edge and ends as far beyond the other. */
    private const val GUST_RUN_UP = 1.5f
    private const val GARDEN_SIZE = 9f

    /** A plant begins to bow this long before the front reaches it ... */
    private const val GUST_RISE_MILLIS = 140.0

    /** ... and afterwards takes about this long to fall back to a third of the bow. */
    private const val GUST_SETTLE_MILLIS = 850.0

    /** What is left of a gust when it counts as over. */
    private const val GUST_TRACE = 0.03

    /** While it is bowed, a plant shakes at this pace. */
    private const val FLUTTER_MILLIS = 520.0

    /** How hard it blows, 0 (still air) upwards; a fine day is 1. */
    fun strength(weather: WeatherType): Float = when (weather) {
        WeatherType.SUNNY -> 1.0f
        WeatherType.CLOUDY -> 1.2f
        WeatherType.RAINY -> 1.5f
    }

    /** The gust of one slot: when it starts, how long it takes to cross, and which way it runs. */
    private class Gust(slot: Long) {
        private val dice = hash(slot)
        val startMillis: Long = slot * GUST_SLOT_MILLIS + (dice and 0xFFFF) * GUST_START_SPREAD_MILLIS / 0x10000
        val crossMillis: Long =
            GUST_CROSS_MIN_MILLIS + ((dice ushr 16) and 0xFFFF) * (GUST_CROSS_MAX_MILLIS - GUST_CROSS_MIN_MILLIS) / 0x10000
        val axis: GustAxis = if ((dice ushr 32) and 1L == 0L) GustAxis.ALONG_X else GustAxis.ALONG_Y

        /** When the front reaches the given distance along the axis. */
        fun arrivalMillis(along: Float): Double =
            startMillis + (along + GUST_RUN_UP).toDouble() / (GARDEN_SIZE + 2 * GUST_RUN_UP) * crossMillis

        fun frontAt(timeMillis: Long): Float =
            (timeMillis - startMillis).toFloat() / crossMillis * (GARDEN_SIZE + 2 * GUST_RUN_UP) - GUST_RUN_UP

        private companion object {
            /** Scrambles a slot number into bits that look unrelated to those of its neighbours. */
            fun hash(slot: Long): Long {
                var h = slot * -7046029254386353131L
                h = h xor (h ushr 32)
                h *= -4417276706812531889L
                return h xor (h ushr 29)
            }
        }
    }

    /** How strongly the gust [gust] is felt at this place and time: 0 (not at all) to 1 (its peak). */
    private fun felt(gust: Gust, timeMillis: Long, gridX: Float, gridY: Float): Double {
        val along = if (gust.axis == GustAxis.ALONG_X) gridX else gridY
        val since = timeMillis - gust.arrivalMillis(along)
        return when {
            since < -GUST_RISE_MILLIS -> 0.0
            since < 0.0 -> 1.0 + since / GUST_RISE_MILLIS
            // Dying away, and then really gone: below a trace it counts as nothing, so that
            // the calm between two gusts is calm.
            else -> ((exp(-since / GUST_SETTLE_MILLIS) - GUST_TRACE) / (1.0 - GUST_TRACE)).coerceAtLeast(0.0)
        }
    }

    /**
     * How much of a gust is felt at ([gridX], [gridY]) at [timeMillis]: 0 in the calm between
     * gusts, 1 at the moment a front passes, falling away afterwards.
     */
    fun gustiness(timeMillis: Long, gridX: Float, gridY: Float): Float {
        val slot = Math.floorDiv(timeMillis, GUST_SLOT_MILLIS)
        // The gust of the slot before may still be settling.
        return max(felt(Gust(slot), timeMillis, gridX, gridY), felt(Gust(slot - 1), timeMillis, gridX, gridY)).toFloat()
    }

    /** The gust that is crossing the garden at [timeMillis], or null in the calm between gusts. */
    fun gustAt(timeMillis: Long, weather: WeatherType): GustFront? {
        val slot = Math.floorDiv(timeMillis, GUST_SLOT_MILLIS)
        for (gust in arrayOf(Gust(slot), Gust(slot - 1))) {
            if (timeMillis >= gust.startMillis && timeMillis <= gust.startMillis + gust.crossMillis) {
                return GustFront(gust.axis, gust.frontAt(timeMillis), strength(weather))
            }
        }
        return null
    }

    /**
     * How far the thing at ([gridX], [gridY]) leans at [timeMillis]: negative to the left,
     * positive to the right, never beyond ±[strength].
     *
     * In the calm it swings gently either way, each tile in its own rhythm. In a gust it is
     * pushed the way the wind is going (to the right for a gust along x, to the left for one
     * along y, as those are the ways they run on screen) and shakes while it is held there.
     */
    fun sway(timeMillis: Long, gridX: Float, gridY: Float, weather: WeatherType): Float {
        val phase = gridX * 0.9 + gridY * 1.7
        val breeze = (CALM_SHARE - 0.03) * sin(2.0 * PI * timeMillis / BREEZE_MILLIS + phase) +
            0.03 * sin(2.0 * PI * timeMillis / (BREEZE_MILLIS * 0.31) + phase * 2.3)

        val slot = Math.floorDiv(timeMillis, GUST_SLOT_MILLIS)
        var push = 0.0
        for (gust in arrayOf(Gust(slot), Gust(slot - 1))) {
            val felt = felt(gust, timeMillis, gridX, gridY)
            if (felt <= 0.0) continue
            val direction = if (gust.axis == GustAxis.ALONG_X) 1.0 else -1.0
            // Held over to one side, and shaking about that.
            val shake = 0.78 + 0.22 * sin(2.0 * PI * timeMillis / FLUTTER_MILLIS + phase * 1.7)
            val candidate = direction * felt * shake * (1.0 - CALM_SHARE)
            if (abs(candidate) > abs(push)) push = candidate
        }
        return ((breeze + push) * strength(weather)).toFloat()
    }
}
