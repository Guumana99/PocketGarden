package com.studioguu.miniaturegarden.ui.render

/** A soft patch of lighter or deeper green laid over the lawn, in grid coordinates. */
data class ShadePatch(val x: Float, val y: Float, val radius: Float, val light: Boolean, val strength: Float)

/**
 * Soft light and shade over the whole lawn (#47).
 *
 * The lawn is laid cell by cell from one picture of grass, which has had its own lighting taken
 * out (tools/prepare_building_art.py `flatten_light`), so the cells no longer show as a chequer.
 * What makes it read as one meadow again is light that ignores the cells: broad, faint patches,
 * each a cell or two across, placed over the garden as a whole and crossing the cells' edges.
 *
 * Worked out from the garden's size alone, so the same garden always looks the same.
 */
object LawnShade {
    /** Patches per cell of lawn, on average. */
    const val PER_CELL = 0.55f

    const val MIN_RADIUS = 0.8f
    const val MAX_RADIUS = 2.2f

    /** The faintest and strongest a patch is drawn (its alpha at the middle). */
    const val MIN_STRENGTH = 0.07f
    const val MAX_STRENGTH = 0.18f

    fun patches(gridSize: Int): List<ShadePatch> {
        var state = gridSize * 2654435761.toInt() xor 0x5bd1e995
        fun next(): Float {
            state = state * 1103515245 + 12345
            return ((state ushr 16) and 0x7FFF) / 32768f
        }
        val count = (gridSize * gridSize * PER_CELL).toInt()
        return List(count) {
            ShadePatch(
                x = next() * gridSize,
                y = next() * gridSize,
                radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * next(),
                light = next() < 0.45f,
                strength = MIN_STRENGTH + (MAX_STRENGTH - MIN_STRENGTH) * next(),
            )
        }
    }

    /** Whether the cell of grass at ([x], [y]) is drawn mirrored: about half of them, in no pattern. */
    fun mirrored(x: Int, y: Int): Boolean {
        var h = x * 73856093 xor (y * 19349663) xor 0x2545F491
        h = h xor (h ushr 15)
        h *= 0x2C1B3C6D
        h = h xor (h ushr 12)
        return (h and 1) == 1
    }

    /** How much larger than its diamond each cell of grass is drawn, so neighbours overlap at the seam. */
    const val CELL_OVERLAP = 1.04f
}
