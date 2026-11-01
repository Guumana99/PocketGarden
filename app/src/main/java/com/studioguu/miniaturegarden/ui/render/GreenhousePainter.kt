package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** The glass of the walls: all but clear, faintly blue, and fainter still in front of the crops. */
private val WALL_GLASS = Color(0xFFBFDCEA)
private const val BACK_GLASS_ALPHA = 0.1f
private const val FRONT_GLASS_ALPHA = 0.05f
private val GLASS_GLINT = Color(0xFFFFFFFF)
private const val BACK_GLINT_ALPHA = 0.16f
private const val FRONT_GLINT_ALPHA = 0.1f

/** The roof: the wood of the walls, in thin lines, over the faintest of glass. */
private val ROOF_FRAME = Color(0xFF3A2C22)
private const val ROOF_FRAME_WIDTH = 0.01f
private const val ROOF_GLASS_ALPHA = 0.06f

/** The streaks of light across a pane: from (along, up) to (along, up), in shares of the wall. */
private val GLINTS = listOf(
    Triple(0.14f to 0.3f, 0.3f to 0.86f, 1f),
    Triple(0.62f to 0.3f, 0.78f to 0.86f, 0.7f),
)

/** The screen position of a point on [wall]: [along] it from its left end, [up] of its height. */
private fun IsometricTransform.onWall(wall: GreenhouseWall, along: Float, up: Float): Offset {
    val left = worldToScreen(wall.from.x, wall.from.y)
    val right = worldToScreen(wall.to.x, wall.to.y)
    return left + (right - left) * along - Offset(0f, tileWidth * GreenhouseLook.WALL_HEIGHT * up)
}

private fun IsometricTransform.onRoof(point: RoofPoint): Offset =
    worldToScreen(point.x, point.y) - Offset(0f, tileWidth * point.height)

private fun outline(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (point in points.drop(1)) lineTo(point.x, point.y)
    close()
}

/** The glass of [wall], laid behind its frame: the wood and the door are drawn over it, and stay solid. */
fun DrawScope.drawGreenhouseGlass(transform: IsometricTransform, wall: GreenhouseWall) {
    val front = wall.side.isFront
    val corners = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f).map { (along, up) -> transform.onWall(wall, along, up) }
    drawPath(outline(corners), WALL_GLASS.copy(alpha = if (front) FRONT_GLASS_ALPHA else BACK_GLASS_ALPHA))
    val glint = if (front) FRONT_GLINT_ALPHA else BACK_GLINT_ALPHA
    for ((start, end, strength) in GLINTS) {
        drawLine(
            GLASS_GLINT.copy(alpha = glint * strength),
            transform.onWall(wall, start.first, start.second),
            transform.onWall(wall, end.first, end.second),
            strokeWidth = transform.tileWidth * 0.008f,
        )
    }
}

/** A stretch of the roof: its panes, then its frames. */
fun DrawScope.drawGreenhouseRoof(transform: IsometricTransform, piece: RoofPiece) {
    for (pane in piece.panes) {
        drawPath(outline(pane.map(transform::onRoof)), WALL_GLASS.copy(alpha = ROOF_GLASS_ALPHA))
    }
    val frames = Path()
    for ((from, to) in piece.frames) {
        val start = transform.onRoof(from)
        val end = transform.onRoof(to)
        frames.moveTo(start.x, start.y)
        frames.lineTo(end.x, end.y)
    }
    drawPath(frames, ROOF_FRAME, style = Stroke(width = transform.tileWidth * ROOF_FRAME_WIDTH))
}
