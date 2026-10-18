package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.StructureData
import kotlin.math.abs

/**
 * Which buildings stand in front of a cell on screen and hide it.
 *
 * Taps are always read on the ground (see [TilePicker]): a tap on a roof or a wall picks the
 * cell of ground under that point, which lies behind the building, and a building can only be
 * picked by its own footprint. What is behind a building is therefore reachable, but not seen;
 * the garden makes the buildings that hide it see-through while it matters.
 *
 * Worked in grid units, independent of zoom: across the screen u = x - y (half tile widths),
 * down it v = x + y (half tile heights). A building is treated as a box over its footprint as
 * tall as [StructureData.heightSteps]; a cell counts as hidden when the middle of the cell lies
 * inside that box's outline and is not part of the footprint itself. A windmill also hides what
 * is under the circle its sails sweep ([WindmillSails.REACH]), which reaches past the side of
 * its footprint that the hub is on.
 */
object Occlusion {

    /** Height steps per half tile height on screen: 16 dp steps against 64 dp tiles. */
    const val STEPS_PER_HALF_TILE = 2f

    /** Whether [building] (a structure) hides the middle of [cell]. */
    fun hides(building: GridObject, cell: GridCell): Boolean {
        val structure = StructureData.fromObjectId(building.objectId) ?: return false
        val w = structure.footprint.width
        val h = structure.footprint.height
        val gx = building.gridX
        val gy = building.gridY
        if (cell.x in gx until gx + w && cell.y in gy until gy + h) return false

        val u = (cell.x - cell.y).toFloat()
        val v = (cell.x + cell.y + 1).toFloat()
        return covers(building, u, v, structure.heightSteps)
    }

    /**
     * Whether the screen point (u, v) lies inside [building]'s outline on screen: the box over
     * its footprint [heightSteps] tall, plus the circle a windmill's sails sweep. [margin] widens
     * the outline on every side, in half tile heights. Used for hiding and for taps
     * ([TapTargets]); a building that is not a structure covers nothing.
     */
    fun covers(building: GridObject, u: Float, v: Float, heightSteps: Float, margin: Float = 0f): Boolean {
        val structure = StructureData.fromObjectId(building.objectId) ?: return false
        val w = structure.footprint.width
        val h = structure.footprint.height
        val gx = building.gridX
        val gy = building.gridY
        if (building.objectId == WindmillSails.MILL_OBJECT_ID && underSails(building, w, h, u, v)) return true
        val uBack = (gx - gy).toFloat()
        val vBack = (gx + gy).toFloat()
        val uLeft = uBack - h
        val uRight = uBack + w
        val uFront = uBack + w - h
        if (u <= uLeft - margin || u >= uRight + margin) return false

        val rise = heightSteps / STEPS_PER_HALF_TILE
        // Past the side corners the outline goes straight up and down.
        val edgeU = u.coerceIn(uLeft, uRight)
        val top = vBack + abs(edgeU - uBack) - rise - margin
        val bottom = (if (edgeU <= uFront) vBack + h + (edgeU - uLeft) else vBack + w + h - (edgeU - uFront)) + margin
        return v > top && v < bottom
    }

    /**
     * Whether the screen point (u, v) lies inside the circle the windmill's sails sweep. A tile
     * width is 2 in u and 4 in v (a tile is half as tall as it is wide), so the circle is an
     * ellipse here.
     */
    private fun underSails(mill: GridObject, w: Int, h: Int, u: Float, v: Float): Boolean {
        val side = if (mill.isFlipped) -1f else 1f
        val hubU = (mill.gridX - mill.gridY + (w - h) / 2f) + side * WindmillSails.HUB_RIGHT * 2f
        val hubV = (mill.gridX + mill.gridY + (w + h) / 2f) - WindmillSails.HUB_UP * 4f
        val du = (u - hubU) / 2f
        val dv = (v - hubV) / 4f
        return du * du + dv * dv < WindmillSails.REACH * WindmillSails.REACH
    }

    /** The instance ids of the buildings that hide [cell]. */
    fun buildingsHiding(cell: GridCell, objects: List<GridObject>): Set<String> =
        objects.filter { hides(it, cell) }.mapTo(HashSet()) { it.instanceId }

    /**
     * The buildings to draw see-through right now. While something is being placed, moved, or
     * the hoe is in hand, every building is: the player is looking for a cell. Otherwise only
     * those hiding the selected cell, and never the selected thing itself.
     */
    fun fadedBuildings(objects: List<GridObject>, selected: GridCell?, editing: Boolean): Set<String> = when {
        editing -> objects.filter { StructureData.fromObjectId(it.objectId) != null }.mapTo(HashSet()) { it.instanceId }
        selected == null -> emptySet()
        else -> buildingsHiding(selected, objects)
    }
}
