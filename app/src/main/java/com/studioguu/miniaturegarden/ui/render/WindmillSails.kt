package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.WeatherType

/**
 * How a windmill's sails turn: steadily round their hub, a little faster when the wind blows
 * harder. Reckoned from the garden's wind clock, so they stop with everything else when the
 * garden is paused, and stand still in the encyclopedia's pictures, which have no clock.
 */
object WindmillSails {
    /** Asset id of the sails, drawn over the mill on its hub ([com.studioguu.miniaturegarden.data.asset.AssetManifest.attachX]). */
    const val SAILS_ASSET_ID = "struct_windmill_blades"

    /** The mill the sails belong to. */
    const val MILL_OBJECT_ID = "struct_windmill"

    /** One full turn takes this long in a fair breeze. */
    const val TURN_MILLIS = 9_000L

    /** How much quicker they go in each kind of weather (the rain brings wind with it). */
    fun speedIn(weather: WeatherType): Float = when (weather) {
        WeatherType.SUNNY -> 1f
        WeatherType.CLOUDY -> 1.25f
        WeatherType.RAINY -> 1.6f
    }

    /**
     * The sails' angle in degrees (0 until 360, clockwise) at [clockMillis] on the wind clock;
     * 0 with no clock. Each mill starts a little further round, by where it stands, so that two
     * mills side by side do not turn as one.
     */
    fun angleAt(clockMillis: Long?, weather: WeatherType = WeatherType.SUNNY, gridX: Int = 0, gridY: Int = 0): Float {
        if (clockMillis == null) return 0f
        val turns = clockMillis.toDouble() * speedIn(weather) / TURN_MILLIS
        val offset = ((gridX * 37 + gridY * 61) % 90).toDouble()
        return (((turns * 360.0) + offset) % 360.0).toFloat()
    }
}
