package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * 2:1 isometric projection between world (grid) space and screen pixels.
 *
 * A world point (x, y) lands on the *top corner* of tile (x, y); the tile itself covers
 * [x, x+1) x [y, y+1), so its diamond runs top -> right -> bottom -> left through
 * (x, y), (x+1, y), (x+1, y+1), (x, y+1). Z lifts a point straight up the screen.
 *
 * All sizes are in pixels and already include the camera zoom.
 */
data class IsometricTransform(
    val tileWidth: Float,
    val tileHeight: Float,
    val heightStep: Float,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun worldToScreen(x: Float, y: Float, z: Float = 0f): Offset = Offset(
        x = (x - y) * (tileWidth / 2f) + offsetX,
        y = (x + y) * (tileHeight / 2f) - z * heightStep + offsetY,
    )

    /** Inverse of [worldToScreen] on the ground plane (z = 0); the result is fractional. */
    fun screenToWorld(screen: Offset): Offset {
        val dx = (screen.x - offsetX) / tileWidth
        val dy = (screen.y - offsetY) / tileHeight
        return Offset(x = dx + dy, y = dy - dx)
    }

    fun tileCenter(x: Int, y: Int, z: Float = 0f): Offset = worldToScreen(x + 0.5f, y + 0.5f, z)

    /** Diamond outline of the rectangle of tiles starting at (x, y). */
    fun tilePath(x: Int, y: Int, z: Float = 0f, spanX: Int = 1, spanY: Int = 1): Path {
        val top = worldToScreen(x.toFloat(), y.toFloat(), z)
        val right = worldToScreen((x + spanX).toFloat(), y.toFloat(), z)
        val bottom = worldToScreen((x + spanX).toFloat(), (y + spanY).toFloat(), z)
        val left = worldToScreen(x.toFloat(), (y + spanY).toFloat(), z)
        return Path().apply {
            moveTo(top.x, top.y)
            lineTo(right.x, right.y)
            lineTo(bottom.x, bottom.y)
            lineTo(left.x, left.y)
            close()
        }
    }
}
