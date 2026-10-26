package com.studioguu.miniaturegarden.domain.model

import java.time.ZoneId

/**
 * What the weather is at any given moment. Plants are brought up to date through one of these,
 * so that time spent away is weighed with the weather each part of it actually had.
 */
fun interface WeatherForecast {
    fun at(epochMillis: Long): WeatherType

    /** When the weather next changes after [epochMillis]; [Long.MAX_VALUE] if it never does. */
    fun nextChangeAfter(epochMillis: Long): Long = Long.MAX_VALUE

    companion object {
        /**
         * The weather a garden at [step] of its first steps runs on: a new game opens on a fine
         * day ([WeatherType.SUNNY]) and stays fine all through its first play
         * ([TutorialStep.isFirstPlay]), so the guided steps never start under rain that has
         * already watered the seed; after that, the garden's own [diary]. Null (not read yet)
         * is the diary.
         */
        fun forGarden(step: TutorialStep?, diary: WeatherForecast): WeatherForecast =
            if (step?.isFirstPlay == true) WeatherType.SUNNY else diary
    }
}

/**
 * A kind of weather. It is also a forecast of itself: that weather, all the time.
 *
 * @property moistureDrainMultiplier scales how fast soil dries out; 0 means it does not dry at all.
 * @property growthMultiplier scales how fast healthy plants grow.
 */
enum class WeatherType(val moistureDrainMultiplier: Float, val growthMultiplier: Float) : WeatherForecast {
    SUNNY(moistureDrainMultiplier = 1.0f, growthMultiplier = 1.0f),
    CLOUDY(moistureDrainMultiplier = 0.7f, growthMultiplier = 1.0f),

    /**
     * A good day for the garden: the rain waters every plant, nothing dries out or moves any
     * closer to withering while it lasts, and everything grows a little faster.
     */
    RAINY(moistureDrainMultiplier = 0.0f, growthMultiplier = 1.2f),

    /**
     * Winter only. Like rain, the snow keeps the soil moist and the plants from withering, but
     * nothing grows any faster for it.
     */
    SNOWY(moistureDrainMultiplier = 0.0f, growthMultiplier = 1.0f);

    /** Whether this weather waters the plants and keeps them from withering. */
    val isProtective: Boolean get() = this == RAINY || this == SNOWY

    override fun at(epochMillis: Long): WeatherType = this

    companion object {
        fun fromName(name: String): WeatherType? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The garden's weather, one kind for each [GameDay], as decided and kept ([WeatherPlanner]). A
 * day not in it (one so far back it was let go) counts as [WeatherType.SUNNY].
 */
class WeatherDiary(val days: Map<Long, WeatherType>, private val zone: ZoneId = ZoneId.systemDefault()) : WeatherForecast {
    override fun at(epochMillis: Long): WeatherType = days[GameDay.of(epochMillis, zone)] ?: WeatherType.SUNNY

    override fun nextChangeAfter(epochMillis: Long): Long = GameDay.startOf(GameDay.of(epochMillis, zone) + 1, zone)

    /** Today's and tomorrow's weather, as of [now]. */
    fun outlook(now: Long): WeatherOutlook {
        val today = GameDay.of(now, zone)
        return WeatherOutlook(days[today] ?: WeatherType.SUNNY, days[today + 1] ?: WeatherType.SUNNY)
    }

    companion object {
        val EMPTY = WeatherDiary(emptyMap())
    }
}

/** What the weather is today and will be tomorrow. The forecast is always right: tomorrow is already decided. */
data class WeatherOutlook(val today: WeatherType, val tomorrow: WeatherType) {
    companion object {
        /**
         * The object id of the weathervane, the ornament that lets the player read the forecast.
         * It has no item, picture or level yet; until it does, the forecast is for the debug menu.
         */
        const val WEATHERVANE_OBJECT_ID = "deco_weathervane"

        /** Whether the garden lets the player see tomorrow's weather: a finished weathervane stands in it. */
        fun isReadable(objects: List<GridObject>): Boolean =
            objects.any { it.objectId == WEATHERVANE_OBJECT_ID && !it.isWorking }
    }
}

/**
 * How likely each kind of weather is, by the season and its phase, in percent:
 *
 *     phase                         sunny  cloudy  rainy  snowy
 *     spring (all of it)              50     30     20     -
 *     early summer (the rainy season) 25     30     45     -
 *     midsummer, late summer          70     15     15     -
 *     autumn (all of it)              55     25     20     -
 *     early and late winter           45     30     10     15
 *     midwinter                       35     25      -     40
 */
object WeatherTable {
    fun odds(season: Season, phase: SeasonPhase): Map<WeatherType, Int> = when (season) {
        Season.SPRING -> mapOf(WeatherType.SUNNY to 50, WeatherType.CLOUDY to 30, WeatherType.RAINY to 20)
        Season.SUMMER -> if (phase == SeasonPhase.EARLY) {
            mapOf(WeatherType.SUNNY to 25, WeatherType.CLOUDY to 30, WeatherType.RAINY to 45)
        } else {
            mapOf(WeatherType.SUNNY to 70, WeatherType.CLOUDY to 15, WeatherType.RAINY to 15)
        }
        Season.AUTUMN -> mapOf(WeatherType.SUNNY to 55, WeatherType.CLOUDY to 25, WeatherType.RAINY to 20)
        Season.WINTER -> if (phase == SeasonPhase.MID) {
            mapOf(WeatherType.SUNNY to 35, WeatherType.CLOUDY to 25, WeatherType.SNOWY to 40)
        } else {
            mapOf(WeatherType.SUNNY to 45, WeatherType.CLOUDY to 30, WeatherType.RAINY to 10, WeatherType.SNOWY to 15)
        }
    }
}

/**
 * Decides a day's weather. The dice come from the garden's own seed and the day alone, so each
 * garden has weather of its own, and the same day always comes out the same. Once decided, a
 * day's weather is kept (the diary), so it never changes afterwards, whatever happens to the
 * season's clock.
 */
object WeatherPlanner {
    /** No kind of weather lasts more than this many days in a row. */
    const val MAX_RUN = 3

    /**
     * The weather of [day], in [season] and [phase], after [previous] (the days just before it,
     * oldest first; only the last [MAX_RUN] matter). When those were all one kind and the dice
     * come out the same again, they are rolled once more among the other kinds.
     */
    fun decide(seed: Long, day: Long, season: Season, phase: SeasonPhase, previous: List<WeatherType>): WeatherType {
        val odds = WeatherTable.odds(season, phase)
        val first = pick(odds, roll(seed, day, 0))
        val recent = previous.takeLast(MAX_RUN)
        if (recent.size < MAX_RUN || recent.any { it != first }) return first
        return pick(odds - first, roll(seed, day, 1))
    }

    /** A kind out of [odds], by [dice] (0 until 1). */
    private fun pick(odds: Map<WeatherType, Int>, dice: Double): WeatherType {
        var left = dice * odds.values.sum()
        for (weather in WeatherType.entries) {
            val share = odds[weather] ?: continue
            if (left < share) return weather
            left -= share
        }
        return odds.keys.last()
    }

    /** A number from 0 until 1 that looks unrelated to that of any other seed, day or [salt]. */
    private fun roll(seed: Long, day: Long, salt: Int): Double {
        var h = seed * -7046029254386353131L + day * -4417276706812531889L + salt * 0x165667B19E3779F9L
        h = (h xor (h ushr 30)) * -4658895280553007687L
        h = (h xor (h ushr 27)) * -7723592293110705685L
        h = h xor (h ushr 31)
        return (h ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}
