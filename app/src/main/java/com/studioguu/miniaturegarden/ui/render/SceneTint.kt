package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.graphics.Color
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.TimeOfDay

/**
 * The light of the time of day, laid over the whole scene: a pale gold in the morning, nothing
 * at all by day, orange in the evening and a deep blue at night. Near the change from one time
 * of day to the next ([TimeOfDay.boundaries]) it moves over gradually, from half an hour before
 * to half an hour after, so dusk falls rather than switching on.
 *
 * Laid over whatever weather there is: under a grey sky the evening is a little more muted, as
 * it is.
 */
object SceneTint {
    /** Half of how long a change from one time of day to the next takes, in hours. */
    const val BLEND_HOURS = 0.5f

    private val MORNING = Color(0xFFFFD49A).copy(alpha = 0.10f)
    private val DAY = Color(0xFFFFD49A).copy(alpha = 0f)
    private val EVENING = Color(0xFFFF8237).copy(alpha = 0.22f)
    private val NIGHT = Color(0xFF111C48).copy(alpha = 0.52f)

    /** The colour of [time] laid over the scene. */
    fun of(time: TimeOfDay): Color = when (time) {
        TimeOfDay.MORNING -> MORNING
        TimeOfDay.DAY -> DAY
        TimeOfDay.EVENING -> EVENING
        TimeOfDay.NIGHT -> NIGHT
    }

    /** The colour laid over the scene at [hourOfDay] (local time) in [season]. */
    fun at(hourOfDay: Float, season: Season): Color {
        val (from, to, t) = blendAt(hourOfDay, season)
        return mix(of(from), of(to), t)
    }

    /**
     * How brightly lamps and windows are lit at [hourOfDay]: 1 through the night, 0 from the
     * morning to the evening, and coming up (or going out) with the change to and from the night.
     */
    fun lampsAt(hourOfDay: Float, season: Season): Float {
        val (from, to, t) = blendAt(hourOfDay, season)
        fun lit(time: TimeOfDay) = if (time == TimeOfDay.NIGHT) 1f else 0f
        return lit(from) + (lit(to) - lit(from)) * t
    }

    /**
     * The two times of day the light is between at [hourOfDay] and how far it has gone from the
     * first to the second (0 to 1). Away from a change, both are the time of day it is.
     */
    fun blendAt(hourOfDay: Float, season: Season): Triple<TimeOfDay, TimeOfDay, Float> {
        val hour = ((hourOfDay % 24f) + 24f) % 24f
        val (day, evening, night) = TimeOfDay.boundaries(season)
        val changes = listOf(
            TimeOfDay.MORNING_HOUR to (TimeOfDay.NIGHT to TimeOfDay.MORNING),
            day to (TimeOfDay.MORNING to TimeOfDay.DAY),
            evening to (TimeOfDay.DAY to TimeOfDay.EVENING),
            night to (TimeOfDay.EVENING to TimeOfDay.NIGHT),
        )
        for ((at, times) in changes) {
            val since = hour - at
            if (since > -BLEND_HOURS && since < BLEND_HOURS) {
                return Triple(times.first, times.second, (since + BLEND_HOURS) / (2 * BLEND_HOURS))
            }
        }
        val now = TimeOfDay.at(hour, season)
        return Triple(now, now, 0f)
    }

    /** [a] moved [t] of the way to [b]; a colour with no strength takes the other's hue. */
    private fun mix(a: Color, b: Color, t: Float): Color {
        val from = if (a.alpha == 0f) b.copy(alpha = 0f) else a
        val to = if (b.alpha == 0f) a.copy(alpha = 0f) else b
        return Color(
            red = from.red + (to.red - from.red) * t,
            green = from.green + (to.green - from.green) * t,
            blue = from.blue + (to.blue - from.blue) * t,
            alpha = from.alpha + (to.alpha - from.alpha) * t,
        )
    }
}
