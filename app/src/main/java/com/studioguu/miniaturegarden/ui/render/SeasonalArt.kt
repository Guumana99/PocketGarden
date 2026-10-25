package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope

// A drawn stand-in for what has no artwork yet: the glass greenhouse (every crop has its pictures
// since #69). The seasons no longer lay petals, leaves or snow over the ground: those white and
// pink specks read as noise on the earth.

private val GLASS = Color(0x33CDEBF7)
private val GLASS_SHINE = Color(0x66FFFFFF)
private val GLASS_FRAME = Color(0xFFF2F4F2)
private val GLASS_FRAME_SHADE = Color(0xFFB8C2C4)

/**
 * The glass house over a greenhouse bed: four panes of glass on a white frame with a pitched
 * glass roof, see-through, so the plant inside shows. Drawn after the plant.
 */
fun DrawScope.drawGreenhouseGlass(transform: IsometricTransform, x: Int, y: Int) {
    val inset = 0.08f
    val x0 = x + inset
    val y0 = y + inset
    val x1 = x + 1 - inset
    val y1 = y + 1 - inset
    // Tall enough for a grown plant to stand inside.
    val wall = 4.6f
    val ridge = 6.4f
    fun p(px: Float, py: Float, z: Float) = transform.worldToScreen(px, py, z)
    fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply {
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }
    val frame = transform.tileWidth * 0.012f
    // The two front walls (the back ones are seen through them and need not be drawn apart).
    val left = quad(p(x0, y1, 0f), p(x1, y1, 0f), p(x1, y1, wall), p(x0, y1, wall))
    val right = quad(p(x1, y0, 0f), p(x1, y1, 0f), p(x1, y1, wall), p(x1, y0, wall))
    drawPath(left, GLASS)
    drawPath(right, GLASS.copy(alpha = 0.16f))
    // The roof: two slopes up to a ridge running along x.
    val midY = (y0 + y1) / 2f
    val roofFront = quad(p(x0, y1, wall), p(x1, y1, wall), p(x1, midY, ridge), p(x0, midY, ridge))
    val roofBack = quad(p(x0, y0, wall), p(x1, y0, wall), p(x1, midY, ridge), p(x0, midY, ridge))
    drawPath(roofBack, GLASS.copy(alpha = 0.12f))
    drawPath(roofFront, GLASS)
    // A streak of light across the front pane.
    drawLine(GLASS_SHINE, p(x0 + 0.15f, y1, wall * 0.25f), p(x0 + 0.35f, y1, wall * 0.9f), frame * 1.6f, StrokeCap.Round)
    // The frame: corner posts, eaves and ridge.
    for ((px, py) in listOf(x0 to y1, x1 to y1, x1 to y0)) {
        drawLine(GLASS_FRAME, p(px, py, 0f), p(px, py, wall), frame, StrokeCap.Round)
    }
    drawLine(GLASS_FRAME_SHADE, p(x0, y0, 0f), p(x0, y0, wall), frame * 0.8f, StrokeCap.Round)
    drawLine(GLASS_FRAME, p(x0, y1, wall), p(x1, y1, wall), frame, StrokeCap.Round)
    drawLine(GLASS_FRAME, p(x1, y1, wall), p(x1, y0, wall), frame, StrokeCap.Round)
    drawLine(GLASS_FRAME, p(x0, midY, ridge), p(x1, midY, ridge), frame, StrokeCap.Round)
    drawLine(GLASS_FRAME, p(x0, y1, wall), p(x0, midY, ridge), frame, StrokeCap.Round)
    drawLine(GLASS_FRAME, p(x1, y1, wall), p(x1, midY, ridge), frame, StrokeCap.Round)
    drawLine(GLASS_FRAME_SHADE, p(x1, y0, wall), p(x1, midY, ridge), frame * 0.8f, StrokeCap.Round)
    // A low sill of the frame round the bed.
    drawLine(GLASS_FRAME, p(x0, y1, 0f), p(x1, y1, 0f), frame * 1.4f, StrokeCap.Round)
    drawLine(GLASS_FRAME, p(x1, y1, 0f), p(x1, y0, 0f), frame * 1.4f, StrokeCap.Round)
}
