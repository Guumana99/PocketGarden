package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.WeatherType
import kotlin.math.PI
import kotlin.math.cos
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

/**
 * The breeze that moves the plants. It is a pure function of time and place, so nothing has to be
 * remembered between frames and every plant can be asked where it is leaning right now.
 */
object Wind {
    /** One slow swing takes about this long. */
    private const val SWAY_MILLIS = 2600.0

    /** A gust takes this long to cross one tile of the garden. */
    private const val GUST_MILLIS_PER_TILE = 420.0
    private const val GUST_MILLIS = 7000.0

    /** How hard it blows, 0 (still air) upwards; a fine day is 1. */
    fun strength(weather: WeatherType): Float = when (weather) {
        WeatherType.SUNNY -> 1.0f
        WeatherType.CLOUDY -> 1.3f
        WeatherType.RAINY -> 1.8f
    }

    /**
     * How far the thing at ([gridX], [gridY]) leans at [timeMillis]: negative to the left,
     * positive to the right, never beyond ±[strength].
     *
     * Each tile swings in its own rhythm, a little out of step with its neighbours, and gusts
     * roll across the garden from one corner, so that the plants bow one after another instead
     * of all at once.
     */
    fun sway(timeMillis: Long, gridX: Float, gridY: Float, weather: WeatherType): Float {
        val phase = gridX * 0.9 + gridY * 1.7
        val swing = sin(2.0 * PI * timeMillis / SWAY_MILLIS + phase)
        val flutter = 0.25 * sin(2.0 * PI * timeMillis / (SWAY_MILLIS * 0.37) + phase * 2.3)
        // 0.35 between gusts, 1 at the height of one.
        val travelled = (timeMillis - (gridX + gridY) * GUST_MILLIS_PER_TILE) / GUST_MILLIS
        val gust = 0.35 + 0.65 * (0.5 + 0.5 * sin(2.0 * PI * travelled))
        return (((swing + flutter) / 1.25) * gust * strength(weather)).toFloat()
    }
}
