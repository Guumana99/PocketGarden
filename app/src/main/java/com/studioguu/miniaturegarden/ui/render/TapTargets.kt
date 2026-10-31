package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.WorkKind

/**
 * What a tap on the garden means when nothing is in hand.
 *
 * Taps are read on the ground ([TilePicker]), so that what stands behind a building stays
 * reachable (see [Occlusion]). But some things are drawn well above their own cells: the
 * "✨ ！" bubble over a finished work site, the little water and food bubbles over a crop, and
 * the walls and roof of a building. Read on the ground alone, a tap on any of them lands a row
 * or two further back, on bare lawn, and only clears the selection. So, in order:
 *
 *  1. a bubble under the finger wins: it is there to be tapped;
 *  2. otherwise, if the ground under the finger holds something, that is what is meant (a crop
 *     behind a cottage is still picked through its roof);
 *  3. otherwise, if the finger is on a building's walls or roof, or just beside them, the
 *     building is picked;
 *  4. otherwise it is the ground: an empty cell, or null off the grid, which clears the
 *     selection as before.
 *
 * While something is being placed or moved, or the hoe is in hand, the player is looking for a
 * cell and only step 4 applies.
 *
 * Screen sizes here follow [IsometricTransform]: they already include the zoom.
 */
object TapTargets {

    /** How far round a bubble a tap still counts, as a share of a tile's width. */
    const val BUBBLE_REACH = 0.3f

    /** How far beside a building's outline a tap still counts, in half tile heights (see [Occlusion]). */
    const val BUILDING_MARGIN = 0.35f

    /** How far from a visitor's body a tap still counts, as a share of a tile's width: they are small and move. */
    const val VISITOR_REACH = 0.55f

    /**
     * How far from a visitor's body a tap still counts, for a tile [tileWidth] pixels wide:
     * never less than [minReach], a finger's half-width, so that with the garden zoomed right out
     * a visitor can still be tapped, and go into the book.
     */
    fun visitorReach(tileWidth: Float, minReach: Float): Float = maxOf(tileWidth * VISITOR_REACH, minReach)

    /**
     * The tile a tap at [screen] stands for.
     *
     * @param grid the garden as [objects] fill it, for what stands on the ground under the finger.
     * @param editing something is being placed or moved, or the hoe is in hand: read the ground only.
     * @param minReach the least distance round a bubble that counts, in pixels: a finger's
     *   half-width, so that a bubble stays easy to hit when the garden is zoomed out.
     * @param plantTop how far above its foot a plant's picture reaches, in pixels, where it is
     *   known: its water or food bubble sits just over it ([statusBubbleCentre]).
     */
    fun resolve(
        screen: Offset,
        transform: IsometricTransform,
        grid: GardenGrid,
        objects: List<GridObject>,
        editing: Boolean,
        minReach: Float = 0f,
        plantTop: (GridObject) -> Float? = { null },
    ): TileCoord? {
        val ground = TilePicker.pick(screen, transform, grid.map.size)
        if (editing) return ground
        bubbleAt(screen, transform, objects, minReach, plantTop)?.let { return TileCoord(it.gridX, it.gridY) }
        if (ground != null && grid.objectAt(ground.x, ground.y) != null) return ground
        buildingAt(screen, transform, objects)?.let { return TileCoord(it.gridX, it.gridY) }
        return ground
    }

    /** The thing whose bubble is nearest [screen], if the tap is close enough to any. */
    fun bubbleAt(
        screen: Offset,
        transform: IsometricTransform,
        objects: List<GridObject>,
        minReach: Float = 0f,
        plantTop: (GridObject) -> Float? = { null },
    ): GridObject? {
        val reach = maxOf(transform.tileWidth * BUBBLE_REACH, minReach)
        return objects
            .mapNotNull { obj -> bubbleCentre(obj, transform, plantTop(obj))?.let { obj to (it - screen).getDistance() } }
            .filter { (_, distance) -> distance <= reach }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    /**
     * Where [obj]'s bubble is drawn (at rest, without its bob), or null when it has none.
     *
     * @param plantTop see [resolve].
     */
    fun bubbleCentre(obj: GridObject, transform: IsometricTransform, plantTop: Float? = null): Offset? = when {
        obj.workKind == WorkKind.READY -> readyBubbleCentre(obj, transform)
        obj.isPlant && (obj.status == PlantStatus.THIRSTY || obj.status == PlantStatus.UNNOURISHED) ->
            statusBubbleCentre(transform.worldToScreen(obj.gridX + 0.5f, obj.gridY + 0.5f, obj.heightLevel.toFloat()), obj, transform.tileWidth, plantTop)
        else -> null
    }

    /**
     * The front-most building whose walls or roof are under [screen] (or within
     * [BUILDING_MARGIN] of them). Work sites count with the height of their sheeting.
     */
    fun buildingAt(screen: Offset, transform: IsometricTransform, objects: List<GridObject>): GridObject? {
        val world = transform.screenToWorld(screen)
        val u = world.x - world.y
        val v = world.x + world.y
        return objects
            .filter { obj ->
                val structure = StructureData.fromObjectId(obj.objectId) ?: return@filter false
                val steps = if (obj.isWorking) workSiteSteps(structure) else structure.heightSteps
                Occlusion.covers(obj, u, v, steps, BUILDING_MARGIN)
            }
            .maxByOrNull { it.gridX + it.footprint.width + it.gridY + it.footprint.height }
    }

    // --- Where things are drawn; shared with GardenCanvas so that taps and pictures agree. ------

    /** How tall a work site's sheeting stands, in height steps. */
    fun workSiteSteps(structure: StructureData?): Float = when (structure) {
        StructureData.HUT -> 7.2f
        StructureData.WELL -> 2.4f
        StructureData.COTTAGE -> 8.2f
        StructureData.WINDMILL -> 10f
        null -> 0f
    }

    /** How tall the work site of [obj] is on screen: what its scaffolding reaches. */
    fun workSiteHeight(obj: GridObject, transform: IsometricTransform): Float {
        val structure = StructureData.fromObjectId(obj.objectId)
        // A fence's site is a small box the height of a quarter tile's width.
        return if (structure == null) transform.tileWidth * 0.24f else workSiteSteps(structure) * transform.heightStep
    }

    /** The radius of the "✨ ！" bubble over a finished work site, in tile widths. */
    const val READY_BUBBLE_RADIUS = 0.2f

    /** Where the tail of the "✨ ！" bubble points: a little above the work site's sheeting. */
    fun readyBubbleTip(obj: GridObject, transform: IsometricTransform): Offset {
        val footprint = obj.footprint
        val centre = transform.worldToScreen(obj.gridX + footprint.width / 2f, obj.gridY + footprint.height / 2f)
        return centre - Offset(0f, workSiteHeight(obj, transform) * 1.2f)
    }

    /** The middle of the "✨ ！" bubble, before it bobs. */
    fun readyBubbleCentre(obj: GridObject, transform: IsometricTransform): Offset =
        readyBubbleTip(obj, transform) - Offset(0f, transform.tileWidth * READY_BUBBLE_RADIUS * READY_BUBBLE_LIFT)

    /** How far above its tip the ready bubble's middle is, in bubble radii. */
    const val READY_BUBBLE_LIFT = 1.35f

    /**
     * The middle of the little water or food bubble over a crop standing on [base]: just over the
     * top of its picture, [plantTop] pixels above its foot (device feedback, after #75: a guess
     * from its growth alone left it floating well above a low plant). Without the picture's
     * height, the guess.
     */
    fun statusBubbleCentre(base: Offset, obj: GridObject, unit: Float, plantTop: Float? = null): Offset =
        if (plantTop != null) {
            base - Offset(0f, plantTop + unit * STATUS_BUBBLE_GAP)
        } else {
            base - Offset(0f, unit * (0.55f * plantScale(obj) + 0.3f))
        }

    /** How far the middle of a water or food bubble is over the top of its plant, in tile widths: its radius and a little. */
    const val STATUS_BUBBLE_GAP = 0.1f
}

/** How large a crop is drawn, from a sprout (0.35) to fully grown (1). */
fun plantScale(obj: GridObject): Float = 0.35f + 0.65f * obj.growthProgress.coerceIn(0f, 1f)
