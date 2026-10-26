package com.studioguu.miniaturegarden.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The garden's days. A day begins at [START_HOUR] in the morning, local time, not at midnight:
 * someone tending the garden late at night is still in the same day, and the weather and the
 * seasons turn over while they sleep. The weather ([WeatherDiary]) and the season's days
 * ([SeasonClock]) are both counted in these days, so they always change together.
 *
 * A day is numbered like [LocalDate.toEpochDay]: the date on which it began.
 */
object GameDay {
    /** The hour (local time) a new day begins. */
    const val START_HOUR = 5

    /** The day [epochMillis] falls in. */
    fun of(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).minusHours(START_HOUR.toLong()).toLocalDate().toEpochDay()

    /** When [day] begins: [START_HOUR] o'clock on its date. */
    fun startOf(day: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(day).atTime(START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
}

/**
 * A third of a season: its first, middle and last [SeasonClock.DAYS_PER_PHASE] days. The screen
 * names them with the season ("春のはじめ", "真夏", "冬のおわり"); the weather leans on them
 * ([WeatherTable]). The shop's stock goes by the season alone.
 */
enum class SeasonPhase {
    EARLY,
    MID,
    LATE;

    companion object {
        /** The phase of the [dayOfSeason]th day of a season, counting from 1. */
        fun ofDay(dayOfSeason: Int): SeasonPhase =
            entries[((dayOfSeason - 1) / SeasonClock.DAYS_PER_PHASE).coerceIn(0, entries.size - 1)]
    }
}

/**
 * The time of day, by the device's own clock (a player abroad sees the local morning). It only
 * changes how the garden looks and who visits it: plants grow, dry and are harvested the same at
 * any hour, and building goes on through the night.
 *
 *     season           morning   day       evening   night
 *     spring, autumn   5-11      11-16     16-19     19-5
 *     summer           5-11      11-17     17-19     19-5
 *     winter           5-11      11-15     15-18     18-5
 */
enum class TimeOfDay {
    MORNING,
    DAY,
    EVENING,
    NIGHT;

    /** An hour well inside this time of day, for the debug menu to show it. */
    fun typicalHour(season: Season): Float {
        val (day, evening, night) = boundaries(season)
        return when (this) {
            MORNING -> 8f
            DAY -> (day + evening) / 2f
            EVENING -> (evening + night) / 2f
            NIGHT -> 22f
        }
    }

    companion object {
        /** When the morning begins, in every season. */
        const val MORNING_HOUR = 5f

        /**
         * When the day, the evening and the night begin in [season] (hours, local time). The
         * morning begins at [MORNING_HOUR].
         */
        fun boundaries(season: Season): Triple<Float, Float, Float> = when (season) {
            Season.SPRING, Season.AUTUMN -> Triple(11f, 16f, 19f)
            Season.SUMMER -> Triple(11f, 17f, 19f)
            Season.WINTER -> Triple(11f, 15f, 18f)
        }

        /** The time of day at [hourOfDay] (local time, 0 until 24, minutes as a fraction) in [season]. */
        fun at(hourOfDay: Float, season: Season): TimeOfDay {
            val hour = ((hourOfDay % 24f) + 24f) % 24f
            val (day, evening, night) = boundaries(season)
            return when {
                hour < MORNING_HOUR -> NIGHT
                hour < day -> MORNING
                hour < evening -> DAY
                hour < night -> EVENING
                else -> NIGHT
            }
        }
    }
}
