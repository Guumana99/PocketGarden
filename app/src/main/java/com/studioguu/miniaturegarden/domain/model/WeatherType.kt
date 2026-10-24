package com.studioguu.miniaturegarden.domain.model

/**
 * What the weather is at any given moment. Plants are brought up to date through one of these,
 * so that time spent away is weighed with the weather each part of it actually had.
 */
fun interface WeatherForecast {
    fun at(epochMillis: Long): WeatherType

    /** The game's own weather: one kind per day, see [WeatherType.forTime]. */
    object Daily : WeatherForecast {
        override fun at(epochMillis: Long): WeatherType = WeatherType.forTime(epochMillis)
    }

    companion object {
        /**
         * The weather a garden at [step] of its first steps runs on: a new game opens on a fine
         * day ([WeatherType.SUNNY]) and stays fine all through its first play
         * ([TutorialStep.isFirstPlay]), so the guided steps never start under rain that has
         * already watered the seed; after that, [Daily]. Null (not read yet) is [Daily].
         */
        fun forGarden(step: TutorialStep?): WeatherForecast = if (step?.isFirstPlay == true) WeatherType.SUNNY else Daily
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
    RAINY(moistureDrainMultiplier = 0.0f, growthMultiplier = 1.2f);

    /** Whether this weather waters the plants and keeps them from withering. */
    val isProtective: Boolean get() = this == RAINY

    override fun at(epochMillis: Long): WeatherType = this

    companion object {
        /** The weather changes at multiples of this (midnight UTC). */
        const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /**
         * Weather for the (UTC) day containing [epochMillis]. Deterministic, so it is stable
         * across restarts: roughly half the days are sunny, a third cloudy, a sixth rainy.
         */
        fun forTime(epochMillis: Long): WeatherType {
            val day = Math.floorDiv(epochMillis, DAY_MILLIS)
            return when (Math.floorMod(day * 2654435761L ushr 7, 6L).toInt()) {
                0, 1, 2 -> SUNNY
                3, 4 -> CLOUDY
                else -> RAINY
            }
        }
    }
}
