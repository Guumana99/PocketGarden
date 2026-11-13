package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.GridObject

/**
 * Keeps small things standing just in front of a big building in front of it (#fb-1113 1).
 *
 * The garden sorts what stands on it by its front-most cell's x + y ([LayerPipeline.depthOf]).
 * That is right for things of one cell, but a building of several cells sorts by its front corner,
 * so a well on the cell to its right front (one past its right side, level with its middle) came
 * out *behind* it, and the building's stone footing was drawn over the well; the same down its
 * left front side, and for a visitor standing there.
 *
 * Here a thing is in front of a building when it is wholly past the building's right side
 * (greater x) and level with some of it in y, or wholly past its left side (greater y) and level
 * with some of it in x, within [REACH] cells: that is where the building's picture and the thing's
 * meet. Such a thing is drawn just after the building, in its own order among the others there.
 * Everything else keeps its depth.
 */
object FootprintOrder {
    /** How many cells out from a building's side its order is looked after. */
    const val REACH = 2

    /** A building of more than one cell: its cells from (x0, y0) to (x1, y1), and its own depth. */
    data class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val depth: Float)

    /** The buildings of more than one cell in [objects], by their front corner's depth as drawn. */
    fun boxesOf(objects: List<GridObject>): List<Box> = objects.mapNotNull { obj ->
        val footprint = obj.footprint
        if (footprint.width * footprint.height <= 1) return@mapNotNull null
        val x1 = obj.gridX + footprint.width - 1
        val y1 = obj.gridY + footprint.height - 1
        Box(obj.gridX, obj.gridY, x1, y1, LayerPipeline.depthOf(x1, y1, obj.heightLevel))
    }

    /** Whether a thing on cells (x0, y0) to (x1, y1) stands in front of [box], beside it. */
    fun isInFrontBeside(x0: Int, y0: Int, x1: Int, y1: Int, box: Box): Boolean {
        val pastRight = x0 > box.x1 && x0 <= box.x1 + REACH && y0 <= box.y1 && y1 >= box.y0
        val pastLeft = y0 > box.y1 && y0 <= box.y1 + REACH && x0 <= box.x1 && x1 >= box.x0
        return pastRight || pastLeft
    }

    /**
     * The depth to draw a thing on cells (x0, y0) to (x1, y1) at, sorted as [depth] until now:
     * after every building in [boxes] it stands in front of, keeping its order among the others
     * there (the first tenth of a step after the building's half step); otherwise [depth].
     */
    fun depthOf(x0: Int, y0: Int, x1: Int, y1: Int, depth: Float, boxes: List<Box>): Float {
        var drawn = depth
        for (box in boxes) {
            if (box.x0 == x0 && box.y0 == y0 && box.x1 == x1 && box.y1 == y1) continue
            if (depth > box.depth || !isInFrontBeside(x0, y0, x1, y1, box)) continue
            val after = box.depth + 0.5f + 0.02f * (depth - box.depth + 10f).coerceIn(0f, 10f)
            drawn = maxOf(drawn, after)
        }
        return drawn
    }
}
