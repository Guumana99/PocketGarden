package com.studioguu.miniaturegarden.ui.render

/**
 * One blade of a lawn coming up: its root ([u], [v], 0..1 across the cell), how tall it stands
 * ([length]) and how wide it is at its foot ([width]), both in cells, and which way it leans
 * ([lean], -1 left to 1 right).
 */
data class LawnBlade(val u: Float, val v: Float, val length: Float, val width: Float, val lean: Float)

/**
 * Where the grass of a lawn still coming up stands on its cell (#61).
 *
 * Before (#60), the cell was split three by three and only some of the parts had a clump of
 * grass: a lawn half grown was a few green blobs on bare earth, and one just sown was nothing at
 * all. Now the blades stand over the whole cell from the start, spread evenly over it, and as
 * the lawn grows there are more of them, and each is longer and broader, until the grass closes
 * over the earth. Blades are only added as it grows, never moved: the ones a young lawn has are
 * the first of the ones it has grown. Where they stand is shuffled per cell, so that no two cells
 * come up in the same pattern; the same cell always does.
 */
object LawnPatches {
    /** The blades stand on a jittered [GRID] x [GRID] grid over the cell: evenly, but not in rows. */
    const val GRID = 14

    /** Blades on a lawn just sown, and on one all but grown. */
    const val MIN_BLADES = 70
    const val MAX_BLADES = GRID * GRID

    /** How tall a blade stands, in cells, just sown and fully grown. */
    const val MIN_LENGTH = 0.045f
    const val MAX_LENGTH = 0.09f

    /** How wide a blade is at its foot, in cells, just sown and fully grown. */
    const val MIN_WIDTH = 0.016f
    const val MAX_WIDTH = 0.03f

    /** How far in from the cell's sides the roots stay, so that the grass keeps to its cell. */
    const val MARGIN = 0.03f

    /** How many blades a lawn [growth] (0 sown .. 1 grown) of the way grown has. */
    fun bladeCount(growth: Float): Int = Math.round(lerp(MIN_BLADES.toFloat(), MAX_BLADES.toFloat(), growth.coerceIn(0f, 1f)))

    /** The blades on cell ([x], [y]) for a lawn [growth] (0 sown .. 1 grown) of the way grown. */
    fun blades(x: Int, y: Int, growth: Float): List<LawnBlade> {
        val g = growth.coerceIn(0f, 1f)
        var state = x * 73856093 xor (y * 19349663) xor 0x1F3D5B79
        fun next(): Float {
            state = state * 1103515245 + 12345
            return ((state ushr 16) and 0x7FFF) / 32767f
        }
        // Every spot of the grid with its own jitter, size and lean, drawn once for the cell.
        val spots = (0 until GRID * GRID).map { p ->
            val span = (1f - 2 * MARGIN) / GRID
            Spot(
                u = MARGIN + (p % GRID + next()) * span,
                v = MARGIN + (p / GRID + next()) * span,
                size = next(),
                lean = next() * 2f - 1f,
            )
        }.toMutableList()
        // Fisher-Yates with the cell's own numbers: the order the blades come up in.
        for (i in spots.size - 1 downTo 1) {
            val j = (next() * (i + 1)).toInt().coerceAtMost(i)
            spots[i] = spots[j].also { spots[j] = spots[i] }
        }
        val length = lerp(MIN_LENGTH, MAX_LENGTH, g)
        val width = lerp(MIN_WIDTH, MAX_WIDTH, g)
        return spots.take(bladeCount(g)).map {
            LawnBlade(it.u, it.v, length * (0.75f + 0.5f * it.size), width * (0.8f + 0.4f * it.size), it.lean)
        }
    }

    private class Spot(val u: Float, val v: Float, val size: Float, val lean: Float)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
