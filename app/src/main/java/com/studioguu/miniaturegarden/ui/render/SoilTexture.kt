package com.studioguu.miniaturegarden.ui.render

/** A speck of something on or round a bed of tilled soil. */
data class SoilSpeck(
    /** Where it lies, in grid coordinates (the tile at (3, 4) spans 3..4 and 4..5). */
    val x: Float,
    val y: Float,
    /** How big it is, as a share of a tile's width. */
    val size: Float,
    /** 0..1: which of the shades it is drawn in, light to dark. */
    val shade: Float,
)

/**
 * Where the grains, clods and drifts of earth of a tilled bed lie.
 *
 * A bed is not a brown square. Its edge is ragged: [outline] wanders in and out of the straight
 * line; loose earth has spilled over it onto the grass ([drift]), thinning out with distance;
 * and its surface is full of [grains], lighter where the earth has dried and darker where it is
 * damp.
 *
 * Everything is worked out from the tile's position alone, so a bed looks the same every time it
 * is drawn, and no two beds look quite alike.
 */
object SoilTexture {
    /** How far in from the edge of its tile a bed starts, on average. */
    const val INSET = 0.05f

    /** How far the edge may stray from that line, either way. */
    const val EDGE_WOBBLE = 0.028f

    /** Loose earth reaches at most this far out over the edge of the tile. */
    const val DRIFT_REACH = 0.1f

    private const val POINTS_PER_SIDE = 5
    const val GRAIN_COUNT = 22
    const val DRIFT_COUNT = 18

    private class Dice(x: Int, y: Int, salt: Int) {
        private var state = (x * 73856093) xor (y * 19349663) xor (salt * 83492791)

        /** The next number, 0 until 1. */
        fun next(): Float {
            state = state * 1103515245 + 12345
            return ((state ushr 16) and 0x7FFF) / 32768f
        }
    }

    /**
     * The edge of the bed on tile ([x], [y]) as a closed ring of points, clockwise from its
     * north corner. The corners stay where they are (so that beds side by side leave an even
     * strip of grass between them); the points between them wander.
     */
    fun outline(x: Int, y: Int): List<Pair<Float, Float>> {
        val dice = Dice(x, y, salt = 1)
        val x0 = x + INSET
        val y0 = y + INSET
        val x1 = x + 1 - INSET
        val y1 = y + 1 - INSET
        val corners = listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1)
        val ring = ArrayList<Pair<Float, Float>>(4 * POINTS_PER_SIDE)
        for (side in 0 until 4) {
            val (ax, ay) = corners[side]
            val (bx, by) = corners[(side + 1) % 4]
            // Outwards from the bed, at a right angle to this side.
            val (nx, ny) = when (side) {
                0 -> 0f to -1f
                1 -> 1f to 0f
                2 -> 0f to 1f
                else -> -1f to 0f
            }
            for (i in 0 until POINTS_PER_SIDE) {
                val t = i / POINTS_PER_SIDE.toFloat()
                val stray = if (i == 0) 0f else (dice.next() * 2f - 1f) * EDGE_WOBBLE
                ring += (ax + (bx - ax) * t + nx * stray) to (ay + (by - ay) * t + ny * stray)
            }
        }
        return ring
    }

    /**
     * Earth that has spilled out of the bed onto the grass round it: most of it right at the
     * edge, less and less further out, and the further out the smaller and fainter (a low
     * [SoilSpeck.shade] is faint).
     */
    fun drift(x: Int, y: Int): List<SoilSpeck> {
        val dice = Dice(x, y, salt = 2)
        return List(DRIFT_COUNT) { i ->
            val along = dice.next()
            // Squared, so that most specks stay close to the edge.
            val out = dice.next().let { it * it } * DRIFT_REACH
            val away = INSET - out // measured from the tile's own edge inwards; negative is outside
            val (sx, sy) = when (i % 4) {
                0 -> (x + along) to (y + away)
                1 -> (x + 1 - away) to (y + along)
                2 -> (x + along) to (y + 1 - away)
                else -> (x + away) to (y + along)
            }
            val near = 1f - out / DRIFT_REACH
            SoilSpeck(sx, sy, size = 0.03f + 0.05f * near * dice.next(), shade = 0.25f + 0.75f * near)
        }
    }

    /** The grains on the bed's surface, all of them well inside its edge. */
    fun grains(x: Int, y: Int): List<SoilSpeck> {
        val dice = Dice(x, y, salt = 3)
        val margin = INSET + EDGE_WOBBLE + 0.03f
        val span = 1f - 2f * margin
        return List(GRAIN_COUNT) {
            SoilSpeck(
                x = x + margin + dice.next() * span,
                y = y + margin + dice.next() * span,
                size = 0.01f + 0.016f * dice.next(),
                shade = dice.next(),
            )
        }
    }

    /** How damp the bed looks, 0 (dry and pale) to 1 (dark): a little different for every bed. */
    fun dampness(x: Int, y: Int): Float = Dice(x, y, salt = 4).next()
}
