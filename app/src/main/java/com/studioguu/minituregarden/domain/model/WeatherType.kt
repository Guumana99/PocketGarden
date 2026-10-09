package com.studioguu.minituregarden.domain.model

/**
 * @property moistureDrainMultiplier scales how fast soil dries out; 0 means it does not dry at all.
 */
enum class WeatherType(val moistureDrainMultiplier: Float) {
    SUNNY(1.0f),
    CLOUDY(0.7f),
    RAINY(0.0f),
}
