package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import com.studioguu.miniaturegarden.domain.model.GridObject
import kotlin.math.floor

data class TileCoord(val x: Int, val y: Int)

/** Maps a touch position back onto the grid's ground plane. */
object TilePicker {

    /** The tile under [screen], or null when the point lies outside the grid. */
    fun pick(
        screen: Offset,
        transform: IsometricTransform,
        gridSize: Int = GridObject.GRID_SIZE,
    ): TileCoord? {
        val world = transform.screenToWorld(screen)
        val x = floor(world.x).toInt()
        val y = floor(world.y).toInt()
        return if (x in 0 until gridSize && y in 0 until gridSize) TileCoord(x, y) else null
    }
}
