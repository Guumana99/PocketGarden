package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.studioguu.miniaturegarden.domain.model.Season

// Drawn stand-ins for what has no artwork yet: the seasons on the ground, the crops added
// with the seasons and after them, and the glass greenhouse. Everything is placed from the cell alone, so it
// looks the same every time and does not shimmer as the camera moves.

/**
 * What a season lays over the lawn of one cell, after the grass itself is drawn.
 *
 * - Spring: a few fallen petals, pink and white.
 * - Summer: the grass a deeper, brighter green.
 * - Autumn: the grass gone golden, with fallen leaves.
 * - Winter: snow over everything but a few tufts, and a glint or two.
 */
object SeasonGround {
    /** The wash laid over the whole cell, or null for none. */
    fun wash(season: Season): Color? = when (season) {
        Season.SPRING -> null
        Season.SUMMER -> Color(0x2236A33A)
        Season.AUTUMN -> Color(0x40D99A3A)
        Season.WINTER -> Color(0xC8F4F7FA)
    }

    /** The small things scattered on a cell: petals, leaves or glints. */
    fun specks(season: Season): List<Color> = when (season) {
        Season.SPRING -> listOf(Color(0xFFF7B6C8), Color(0xFFFFFFFF), Color(0xFFF7B6C8))
        Season.SUMMER -> emptyList()
        Season.AUTUMN -> listOf(Color(0xFFC8642A), Color(0xFFE0A030), Color(0xFFA8432A))
        Season.WINTER -> listOf(Color(0xFFFFFFFF), Color(0xFFDCE8F2))
    }
}

fun DrawScope.drawSeasonGround(transform: IsometricTransform, x: Int, y: Int, season: Season) {
    SeasonGround.wash(season)?.let { drawPath(transform.tilePath(x, y), it) }
    val specks = SeasonGround.specks(season)
    if (specks.isEmpty()) return
    val unit = transform.tileWidth
    var seed = x * 92821 xor y * 68917 xor season.ordinal * 7
    fun next(): Float {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 16) and 0x7FFF) / 32767f
    }
    // Only some cells have anything on them, so the scatter does not look like a pattern.
    val count = (next() * 3.2f).toInt()
    repeat(count) { i ->
        val at = transform.worldToScreen(x + 0.15f + next() * 0.7f, y + 0.15f + next() * 0.7f)
        val color = specks[i % specks.size]
        when (season) {
            Season.AUTUMN -> rotate(next() * 360f, pivot = at) {
                drawOval(color, at - Offset(unit * 0.03f, unit * 0.015f), Size(unit * 0.06f, unit * 0.03f))
            }
            Season.WINTER -> drawCircle(color, unit * 0.012f, at)
            else -> drawOval(color, at - Offset(unit * 0.018f, unit * 0.01f), Size(unit * 0.036f, unit * 0.02f))
        }
    }
}

private val STEM = Color(0xFF5E8C3A)
private val STEM_DRY = Color(0xFF8D6E63)

/** How tall a plant stands at [growth] (0..1), as a share of its full height. */
private fun grown(growth: Float): Float = 0.35f + 0.65f * growth.coerceIn(0f, 1f)

/** Cosmos: a thin, branching stem with feathery leaves and pink eight-petalled flowers once grown. */
fun DrawScope.drawCosmos(base: Offset, unit: Float, growth: Float, withered: Boolean) {
    val s = grown(growth)
    val stem = if (withered) STEM_DRY else STEM
    val top = base - Offset(0f, unit * 0.6f * s)
    val side = base + Offset(unit * 0.08f * s, -unit * 0.42f * s)
    drawLine(stem, base, top, unit * 0.022f, StrokeCap.Round)
    drawLine(stem, base - Offset(0f, unit * 0.22f * s), side, unit * 0.018f, StrokeCap.Round)
    // Feathery leaves: short strokes along the stem.
    for (i in 1..3) {
        val at = base - Offset(0f, unit * 0.12f * i * s)
        drawLine(stem, at, at + Offset(-unit * 0.07f * s, -unit * 0.03f * s), unit * 0.01f, StrokeCap.Round)
    }
    if (growth < 0.5f) return
    val petal = if (withered) Color(0xFFBCAAA4) else Color(0xFFF48FB1)
    for ((center, r) in listOf(top to unit * 0.07f * s, side to unit * 0.05f * s)) {
        for (k in 0 until 8) {
            rotate(k * 45f, pivot = center) {
                drawOval(petal, center + Offset(-r * 0.3f, -r * 1.1f), Size(r * 0.6f, r))
            }
        }
        drawCircle(Color(0xFFFFD54F), r * 0.32f, center)
    }
}

/** Snowdrop: two strap leaves and white bells hanging from bowed stems. */
fun DrawScope.drawSnowdrop(base: Offset, unit: Float, growth: Float, withered: Boolean) {
    val s = grown(growth)
    val leaf = if (withered) STEM_DRY else Color(0xFF6E9E68)
    drawLine(leaf, base, base + Offset(-unit * 0.06f * s, -unit * 0.32f * s), unit * 0.03f, StrokeCap.Round)
    drawLine(leaf, base, base + Offset(unit * 0.05f * s, -unit * 0.28f * s), unit * 0.03f, StrokeCap.Round)
    if (growth < 0.4f) return
    val bell = if (withered) Color(0xFFBCAAA4) else Color(0xFFFFFFFF)
    for (dx in listOf(-0.03f, 0.04f)) {
        val crown = base + Offset(unit * dx * s, -unit * 0.42f * s)
        val hang = crown + Offset(unit * 0.05f * s, unit * 0.04f * s)
        val path = Path().apply {
            moveTo(base.x + unit * dx * 0.5f * s, base.y)
            quadraticTo(crown.x, crown.y - unit * 0.04f * s, hang.x, hang.y)
        }
        drawPath(path, leaf, style = Stroke(width = unit * 0.012f, cap = StrokeCap.Round))
        drawOval(bell, hang - Offset(unit * 0.025f * s, 0f), Size(unit * 0.05f * s, unit * 0.07f * s))
        if (!withered) drawCircle(Color(0xFF9CCC65), unit * 0.008f * s, hang + Offset(0f, unit * 0.065f * s))
    }
}

/** Mint: a low, bushy clump of rounded leaves in two greens. */
fun DrawScope.drawMint(base: Offset, unit: Float, growth: Float, withered: Boolean) {
    val s = grown(growth)
    val dark = if (withered) STEM_DRY else Color(0xFF4E9A5A)
    val light = if (withered) Color(0xFFA1887F) else Color(0xFF7CC47F)
    val leaves = listOf(
        Offset(-0.09f, -0.06f), Offset(0.09f, -0.07f), Offset(0f, -0.13f),
        Offset(-0.05f, -0.2f), Offset(0.06f, -0.21f), Offset(0f, -0.28f),
    )
    leaves.forEachIndexed { i, (dx, dy) ->
        val at = base + Offset(unit * dx * s, unit * dy * s)
        drawOval(if (i % 2 == 0) dark else light, at - Offset(unit * 0.05f * s, unit * 0.03f * s), Size(unit * 0.1f * s, unit * 0.06f * s))
    }
}

private val FRUIT_DRY = Color(0xFFBCAAA4)

/** Tomato: an upright bush tied to a cane, hung with red tomatoes once grown (green before). */
fun DrawScope.drawTomato(base: Offset, unit: Float, growth: Float, withered: Boolean) {
    val s = grown(growth)
    val stem = if (withered) STEM_DRY else STEM
    val leaf = if (withered) Color(0xFFA1887F) else Color(0xFF4F8F3F)
    val top = base - Offset(0f, unit * 0.55f * s)
    // The cane it is tied to, a little behind the stem.
    drawLine(Color(0xFFB08A5A), base + Offset(unit * 0.03f, 0f), top + Offset(unit * 0.03f, -unit * 0.04f), unit * 0.012f, StrokeCap.Round)
    drawLine(stem, base, top, unit * 0.022f, StrokeCap.Round)
    for (i in 1..3) {
        val at = base - Offset(0f, unit * 0.15f * i * s)
        val dx = if (i % 2 == 0) 1f else -1f
        drawOval(leaf, at + Offset(if (dx > 0) 0f else -unit * 0.11f * s, -unit * 0.03f * s), Size(unit * 0.11f * s, unit * 0.055f * s))
    }
    if (growth < 0.45f) return
    val fruit = when {
        withered -> FRUIT_DRY
        growth >= 1f -> Color(0xFFE53935)
        else -> Color(0xFF9CCC65)
    }
    for ((dx, dy) in listOf(-0.06f to -0.2f, 0.06f to -0.3f, -0.04f to -0.4f)) {
        val at = base + Offset(unit * dx * s, unit * dy * s)
        drawCircle(fruit, unit * 0.04f * s, at)
        if (!withered) drawCircle(Color(0x55FFFFFF), unit * 0.012f * s, at + Offset(-unit * 0.012f * s, -unit * 0.012f * s))
    }
}

/** Strawberry: a low rosette of three-part leaves with white flowers, then red berries. */
fun DrawScope.drawStrawberry(base: Offset, unit: Float, growth: Float, withered: Boolean) {
    val s = grown(growth)
    val leaf = if (withered) STEM_DRY else Color(0xFF3F8A4A)
    for ((dx, dy) in listOf(-0.1f to -0.06f, 0.1f to -0.06f, -0.04f to -0.14f, 0.05f to -0.15f)) {
        val at = base + Offset(unit * dx * s, unit * dy * s)
        for (k in -1..1) {
            rotate(k * 40f, pivot = at) {
                drawOval(leaf, at - Offset(unit * 0.025f * s, unit * 0.06f * s), Size(unit * 0.05f * s, unit * 0.06f * s))
            }
        }
    }
    if (growth < 0.4f) return
    for ((dx, dy) in listOf(-0.07f to -0.02f, 0.08f to -0.03f, 0f to -0.08f)) {
        val at = base + Offset(unit * dx * s, unit * dy * s)
        if (growth < 0.75f && !withered) {
            drawCircle(Color(0xFFFFFFFF), unit * 0.022f * s, at)
            drawCircle(Color(0xFFFFD54F), unit * 0.008f * s, at)
        } else {
            val berry = Path().apply {
                moveTo(at.x - unit * 0.03f * s, at.y - unit * 0.02f * s)
                lineTo(at.x + unit * 0.03f * s, at.y - unit * 0.02f * s)
                lineTo(at.x, at.y + unit * 0.045f * s)
                close()
            }
            drawPath(berry, if (withered) FRUIT_DRY else Color(0xFFE53950))
            drawOval(if (withered) STEM_DRY else Color(0xFF66BB6A), at - Offset(unit * 0.03f * s, unit * 0.03f * s), Size(unit * 0.06f * s, unit * 0.018f * s))
        }
    }
}

/** Watermelon: a sprawl of vine and leaves along the ground, with a striped melon lying in it. */
fun DrawScope.drawWatermelon(base: Offset, unit: Float, growth: Float, withered: Boolean) {
    val s = grown(growth)
    val vine = if (withered) STEM_DRY else Color(0xFF558B2F)
    val path = Path().apply {
        moveTo(base.x - unit * 0.2f * s, base.y)
        quadraticTo(base.x - unit * 0.05f * s, base.y - unit * 0.12f * s, base.x + unit * 0.2f * s, base.y - unit * 0.04f * s)
    }
    drawPath(path, vine, style = Stroke(width = unit * 0.014f, cap = StrokeCap.Round))
    for ((dx, dy) in listOf(-0.16f to -0.04f, -0.02f to -0.1f, 0.14f to -0.07f)) {
        val at = base + Offset(unit * dx * s, unit * dy * s)
        drawOval(vine, at - Offset(unit * 0.045f * s, unit * 0.04f * s), Size(unit * 0.09f * s, unit * 0.06f * s))
    }
    if (growth < 0.4f) return
    // The melon swells from a small green ball to a big striped one.
    val r = unit * (0.05f + 0.07f * ((growth - 0.4f) / 0.6f).coerceIn(0f, 1f))
    val center = base + Offset(unit * 0.02f, -r * 0.7f)
    val rind = if (withered) FRUIT_DRY else Color(0xFF2E7D32)
    drawOval(rind, center - Offset(r * 1.2f, r * 0.85f), Size(r * 2.4f, r * 1.7f))
    if (!withered) {
        for (k in -1..1) {
            drawLine(Color(0xFF1B5E20), center + Offset(k * r * 0.6f, -r * 0.75f), center + Offset(k * r * 0.6f, r * 0.75f), r * 0.16f, StrokeCap.Round)
        }
        drawOval(Color(0x44FFFFFF), center - Offset(r * 0.7f, r * 0.6f), Size(r * 0.6f, r * 0.3f))
    }
}

/** Lavender: a tuft of grey-green spikes topped with purple flower heads. */
fun DrawScope.drawLavender(base: Offset, unit: Float, growth: Float, withered: Boolean) {
    val s = grown(growth)
    val stem = if (withered) STEM_DRY else Color(0xFF7FA07A)
    val flower = if (withered) FRUIT_DRY else Color(0xFF9575CD)
    for (dx in listOf(-0.1f, -0.05f, 0f, 0.05f, 0.1f)) {
        val tip = base + Offset(unit * dx * 1.4f * s, -unit * (0.42f - kotlin.math.abs(dx)) * s)
        drawLine(stem, base, tip, unit * 0.012f, StrokeCap.Round)
        if (growth < 0.5f) continue
        for (j in 0..3) {
            val at = tip + (base - tip) * (j * 0.08f)
            drawOval(flower, at - Offset(unit * 0.016f * s, unit * 0.014f * s), Size(unit * 0.032f * s, unit * 0.028f * s))
        }
    }
}

/** Pumpkin: big round leaves on a vine and an orange pumpkin, ribbed, with a curly stalk. */
fun DrawScope.drawPumpkin(base: Offset, unit: Float, growth: Float, withered: Boolean) {
    val s = grown(growth)
    val leaf = if (withered) STEM_DRY else Color(0xFF689F38)
    for ((dx, dy) in listOf(-0.15f to -0.06f, 0.15f to -0.08f, 0f to -0.16f)) {
        val at = base + Offset(unit * dx * s, unit * dy * s)
        drawCircle(leaf, unit * 0.06f * s, at)
    }
    if (growth < 0.4f) return
    val r = unit * (0.05f + 0.08f * ((growth - 0.4f) / 0.6f).coerceIn(0f, 1f))
    val center = base + Offset(0f, -r * 0.75f)
    val skin = when {
        withered -> FRUIT_DRY
        growth >= 1f -> Color(0xFFF57C00)
        else -> Color(0xFFC0CA33)
    }
    val rib = if (withered) Color(0xFFA1887F) else skin.copy(red = skin.red * 0.85f, green = skin.green * 0.8f)
    // Three overlapping lobes make the ribs.
    drawOval(rib, center - Offset(r * 1.3f, r * 0.85f), Size(r * 1.4f, r * 1.7f))
    drawOval(rib, center - Offset(-r * 0.1f, r * 0.85f), Size(r * 1.2f, r * 1.7f))
    drawOval(skin, center - Offset(r * 0.6f, r * 0.9f), Size(r * 1.2f, r * 1.8f))
    drawLine(if (withered) STEM_DRY else Color(0xFF6D4C41), center - Offset(0f, r * 0.85f), center - Offset(-r * 0.2f, r * 1.2f), r * 0.2f, StrokeCap.Round)
}

/** Poinsettia: a bushy plant whose top leaves turn red in a star round little yellow flowers. */
fun DrawScope.drawPoinsettia(base: Offset, unit: Float, growth: Float, withered: Boolean) {
    val s = grown(growth)
    val green = if (withered) STEM_DRY else Color(0xFF2E7D32)
    val center = base - Offset(0f, unit * 0.3f * s)
    drawLine(green, base, center, unit * 0.02f, StrokeCap.Round)
    // Green leaves below, red bracts on top once it is grown enough.
    for (k in 0 until 6) {
        rotate(k * 60f + 30f, pivot = center + Offset(0f, unit * 0.06f * s)) {
            val at = center + Offset(0f, unit * 0.06f * s)
            drawOval(green, at - Offset(unit * 0.025f * s, unit * 0.13f * s), Size(unit * 0.05f * s, unit * 0.13f * s))
        }
    }
    if (growth < 0.5f) return
    val red = if (withered) FRUIT_DRY else Color(0xFFD32F2F)
    for (k in 0 until 6) {
        rotate(k * 60f, pivot = center) {
            drawOval(red, center - Offset(unit * 0.022f * s, unit * 0.11f * s), Size(unit * 0.044f * s, unit * 0.11f * s))
        }
    }
    drawCircle(if (withered) STEM_DRY else Color(0xFFFFD54F), unit * 0.018f * s, center)
}

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
