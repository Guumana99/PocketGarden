package com.studioguu.miniaturegarden.domain.model

/**
 * @property moistureDrainMultiplier scales how fast soil dries out; 0 means it does not dry at all.
 * @property growthMultiplier scales how fast healthy plants grow.
 */
enum class WeatherType(val moistureDrainMultiplier: Float, val growthMultiplier: Float) {
    SUNNY(moistureDrainMultiplier = 1.0f, growthMultiplier = 1.0f),
    CLOUDY(moistureDrainMultiplier = 0.7f, growthMultiplier = 1.0f),

    /** A good day for the garden: the soil stays moist and everything grows a little faster. */
    RAINY(moistureDrainMultiplier = 0.0f, growthMultiplier = 1.2f);

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

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
