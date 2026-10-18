package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.WeatherType

/**
 * How a windmill's sails turn: steadily round their hub, a little faster when the wind blows
 * harder. Reckoned from the garden's wind clock, so they stop with everything else when the
 * garden is paused, and stand still in the encyclopedia's pictures, which have no clock.
 */
object WindmillSails {
    /** Asset id of the sails, drawn over the mill on its hub ([com.studioguu.miniaturegarden.data.asset.AssetManifest.attachX]). */
    const val SAILS_ASSET_ID = TileSetDefinition.WINDMILL_SAILS

    /** The mill the sails belong to. */
    const val MILL_OBJECT_ID = "struct_windmill"

    /**
     * Where the sails turn, in tile widths from the ground under the middle of the mill's
     * footprint: the hub is [HUB_RIGHT] to the right (to the left on a mill turned round) and
     * [HUB_UP] up, and the tips of the sails sweep a circle [REACH] round it. Measured off the
     * pictures in the atlas (BuildingArtTest keeps them in step); [Occlusion] uses them, since
     * the sails reach past the side of the footprint and hide what is behind them there.
     */
    const val HUB_RIGHT = 0.67f
    const val HUB_UP = 1.57f
    const val REACH = 1.0f

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
