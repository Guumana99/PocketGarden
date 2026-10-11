package com.studioguu.miniaturegarden.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.ui.render.IsometricTransform
import com.studioguu.miniaturegarden.ui.render.TileCoord

/** Grid lines along both axes of a [gridSize] x [gridSize] board. */
fun DrawScope.drawIsometricGrid(
    transform: IsometricTransform,
    gridSize: Int,
    color: Color = Color.White.copy(alpha = 0.35f),
    strokeWidth: Float = 1.dp.toPx(),
) {
    val max = gridSize.toFloat()
    for (i in 0..gridSize) {
        val t = i.toFloat()
        drawLine(color, transform.worldToScreen(t, 0f), transform.worldToScreen(t, max), strokeWidth)
        drawLine(color, transform.worldToScreen(0f, t), transform.worldToScreen(max, t), strokeWidth)
    }
}

/**
 * Translucent fill marking the block of tiles that starts at [tile] and spans [spanX] x [spanY]
 * (one tile by default); drawn under objects.
 */
fun DrawScope.drawTileHighlight(
    transform: IsometricTransform,
    tile: TileCoord,
    spanX: Int = 1,
    spanY: Int = 1,
    color: Color = Color(0xFFFFF176).copy(alpha = 0.45f),
) {
    drawPath(transform.tilePath(tile.x, tile.y, spanX = spanX, spanY = spanY), color)
}

/** Outline of the same block; drawn over objects so the selection stays visible behind tall sprites. */
fun DrawScope.drawTileOutline(
    transform: IsometricTransform,
    tile: TileCoord,
    spanX: Int = 1,
    spanY: Int = 1,
    color: Color = Color(0xFFFFF59D),
    strokeWidth: Float = 2.5.dp.toPx(),
) {
    drawPath(transform.tilePath(tile.x, tile.y, spanX = spanX, spanY = spanY), color, style = Stroke(strokeWidth))
}
