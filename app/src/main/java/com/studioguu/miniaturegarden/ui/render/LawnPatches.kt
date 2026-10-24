package com.studioguu.miniaturegarden.ui.render

/** A round clump of grass on a cell: its middle ([u], [v], 0..1 across the cell) and [radius], in cells. */
data class LawnClump(val u: Float, val v: Float, val radius: Float)

/**
 * Where the grass of a lawn still coming up stands on its cell (#60).
 *
 * Before, a lawn part of the way grown was the whole picture of grass laid over its cell at a
 * third or two thirds strength: a thin wash of green over the earth, like watery paint. Now the
 * grass is solid where it has come up and absent where it has not: the cell is split three by
 * three, and as many of those parts as the stage covers ([LawnStage.cover] of nine) get a round
 * clump, a little larger than the part so that clumps run into one another. Which parts fill
 * first is shuffled per cell, so that no two cells come up in the same pattern; the same cell
 * always does.
 */
object LawnPatches {
    /** The parts a cell is split into, along each side. */
    const val GRID = 3

    /** A clump's radius, in cells: a little over half a part (1/6), so neighbours join. */
    const val MIN_RADIUS = 0.2f
    const val MAX_RADIUS = 0.26f

    /** How far a clump's middle may wander from its part's middle, in cells. */
    const val JITTER = 0.05f

    /** The clumps on cell ([x], [y]) for a lawn covering [cover] (0..1) of it: none at 0, every part at 1. */
    fun clumps(x: Int, y: Int, cover: Float): List<LawnClump> {
        val count = Math.round(cover.coerceIn(0f, 1f) * GRID * GRID)
        if (count == 0) return emptyList()
        var state = x * 73856093 xor (y * 19349663) xor 0x1F3D5B79
        fun next(): Float {
            state = state * 1103515245 + 12345
            return ((state ushr 16) and 0x7FFF) / 32767f
        }
        val parts = (0 until GRID * GRID).toMutableList()
        // Fisher-Yates with the cell's own numbers: the order the parts fill in.
        for (i in parts.size - 1 downTo 1) {
            val j = (next() * (i + 1)).toInt().coerceAtMost(i)
            parts[i] = parts[j].also { parts[j] = parts[i] }
        }
        val part = 1f / GRID
        return parts.take(count).map { p ->
            LawnClump(
                u = (p % GRID + 0.5f) * part + (next() - 0.5f) * 2f * JITTER,
                v = (p / GRID + 0.5f) * part + (next() - 0.5f) * 2f * JITTER,
                radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * next(),
            )
        }
    }
}
