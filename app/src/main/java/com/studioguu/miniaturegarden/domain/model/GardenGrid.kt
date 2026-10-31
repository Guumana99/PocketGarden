package com.studioguu.miniaturegarden.domain.model

/** Why something can or cannot be placed. */
enum class PlacementCheck {
    OK,

    /** Part of the footprint lies outside the grid. */
    OUT_OF_BOUNDS,

    /** Part of the footprint is on river, whether from the map or laid by the player. */
    NOT_BUILDABLE,

    /** Part of the footprint is already covered by another object. */
    OCCUPIED,
}

/** Where a bridge goes, or why it does not: see [GardenGrid.bridgeAcross]. */
sealed interface BridgeFit {
    /** It goes with its first cell at [origin]; [longer] over a river two cells wide. */
    data class Across(val origin: GridCell, val longer: Boolean) : BridgeFit

    data class Refused(val why: BridgeProblem) : BridgeFit
}

enum class BridgeProblem {
    /** There is no river where it would go. */
    NO_RIVER,

    /** The river is wider than a bridge reaches. */
    TOO_WIDE,

    /** One of its ends would be outside the garden. */
    OUTSIDE,

    /** Something stands where one of its ends would rest, or another bridge is in the way. */
    ENDS_TAKEN,
}

/** Which layer of a cell something goes into. */
enum class PlacementKind {
    /** Stands on the ground: plants, decorations, structures. One per cell. */
    OBJECT,

    /** Paving laid on the ground. One per cell, and objects may stand on it. */
    FLOOR,

    /** Replaces the ground itself (river). Takes the whole cell: no floor, nothing on top. */
    TERRAIN;

    companion object {
        fun forObjectId(objectId: String): PlacementKind = when {
            objectId.startsWith(GridObject.TERRAIN_ID_PREFIX) -> TERRAIN
            // A pond is ground too: no paving under it, nothing standing in it.
            objectId == GridObject.POND_OBJECT_ID -> TERRAIN
            objectId.startsWith(GridObject.FLOOR_ID_PREFIX) -> FLOOR
            else -> OBJECT
        }
    }
}

/**
 * The garden as a whole: the terrain plus everything on it. A cell has two layers, a floor
 * (paving) and an occupant (whatever stands there); every object counts on all the cells of
 * its footprint.
 *
 * A lawn is an occupant too, with one exception (#55): a fence or edging may stand on a lawn, and a
 * lawn be sown under it ([sharesCellWithLawn]). Such a cell holds both; the fence is what stands
 * there ([occupantAt]), and the lawn lies under it ([lawnAt]).
 */
class GardenGrid(
    val map: GardenMap,
    objects: List<GridObject>,
) {
    private val floors: Map<GridCell, GridObject> = buildMap {
        for (obj in objects) if (obj.isFloor) for (cell in obj.occupiedCells()) put(cell, obj)
    }
    private val lawns: Map<GridCell, GridObject> = buildMap {
        for (obj in objects) if (LawnGrowth.isLawn(obj)) for (cell in obj.occupiedCells()) put(cell, obj)
    }
    private val standing: Map<GridCell, GridObject> = buildMap {
        for (obj in objects) if (!obj.isFloor && !LawnGrowth.isLawn(obj) && !obj.isBridge) for (cell in obj.occupiedCells()) put(cell, obj)
    }

    /** A bridge stands over its river, in a layer of its own: the river goes on under it. */
    private val bridges: Map<GridCell, GridObject> = buildMap {
        for (obj in objects) if (obj.isBridge) for (cell in obj.occupiedCells()) put(cell, obj)
    }

    /**
     * What stands on (x, y), or the river tile there; whichever of the object's cells that is.
     * A lawn counts when nothing else stands on it.
     */
    fun occupantAt(x: Int, y: Int): GridObject? = GridCell(x, y).let { standing[it] ?: lawns[it] }

    /** The lawn on (x, y), if any, whether or not a fence stands on it. */
    fun lawnAt(x: Int, y: Int): GridObject? = lawns[GridCell(x, y)]

    /** The paving on (x, y), if any. */
    fun floorAt(x: Int, y: Int): GridObject? = floors[GridCell(x, y)]

    /** Whether (x, y) is soil ([GridObject.isSoil]): what the hoe made, or a greenhouse bed. */
    fun isTilled(x: Int, y: Int): Boolean = floorAt(x, y)?.isSoil == true

    /**
     * What the ground of (x, y) is, for what may be planted there ([PlantGround]): water, then
     * whatever is laid on the cell (a bed, a greenhouse, paving, a lawn, ground cover), and
     * open earth where nothing is (the wasteland, or ground the hoe has begun to break). Whatever
     * stands there otherwise (a plant, a bench) does not change it.
     */
    fun groundAt(x: Int, y: Int): Ground {
        val cell = GridCell(x, y)
        val floor = floors[cell]
        val thing = standing[cell]
        return when {
            !map.isBuildable(x, y) || thing?.isTerrain == true || thing?.isPond == true -> Ground.WATER
            floor?.isGreenhouse == true -> Ground.GREENHOUSE
            floor?.isTilledSoil == true -> Ground.TILLED_SOIL
            floor?.isLooseSoil == true -> Ground.LOOSE_SOIL
            floor != null -> Ground.PAVING
            cell in lawns -> Ground.LAWN
            thing?.isGroundCover == true -> Ground.GROUND_COVER
            else -> Ground.BARE_EARTH
        }
    }

    /** Whether (x, y) is under glass, where anything grows whatever the season. */
    fun isUnderGlass(x: Int, y: Int): Boolean = floorAt(x, y)?.isGreenhouse == true

    /** Whether a symbol building stands in the garden, finished or not. */
    val hasSymbol: Boolean = objects.any { it.isSymbol }

    /** What a tap on (x, y) means: the bridge over it, the thing standing there, or else the paving under it. */
    fun objectAt(x: Int, y: Int): GridObject? = bridges[GridCell(x, y)] ?: occupantAt(x, y) ?: floorAt(x, y)

    /** The bridge over (x, y), if any. */
    fun bridgeAt(x: Int, y: Int): GridObject? = bridges[GridCell(x, y)]

    /**
     * Where a bridge goes for a tap on (x, y), running along gridY, or along gridX if [flipped]: from
     * bank to bank across the river at the tap, or just beside it. The river may be one or two
     * cells wide along that line, and each end rests on land. What only lies on the ground
     * (a lawn, ground cover, paving) can stay under an end; a bed of soil or anything standing there
     * cannot.
     */
    fun bridgeAcross(x: Int, y: Int, flipped: Boolean): BridgeFit {
        val dx = if (flipped) 1 else 0
        val dy = 1 - dx
        fun cell(i: Int) = GridCell(x + dx * i, y + dy * i)
        fun river(i: Int) = standing[cell(i)]?.isTerrain == true
        val at = listOf(0, 1, -1).firstOrNull { river(it) } ?: return BridgeFit.Refused(BridgeProblem.NO_RIVER)
        var first = at
        while (river(first - 1)) first--
        var last = at
        while (river(last + 1)) last++
        val width = last - first + 1
        if (width > MAX_BRIDGED_RIVER) return BridgeFit.Refused(BridgeProblem.TOO_WIDE)
        val cells = (first - 1..last + 1).map(::cell)
        if (cells.any { !map.isInside(it.x, it.y) }) return BridgeFit.Refused(BridgeProblem.OUTSIDE)
        if (cells.any { it in bridges } || !restsOn(cells.first()) || !restsOn(cells.last())) return BridgeFit.Refused(BridgeProblem.ENDS_TAKEN)
        return BridgeFit.Across(cells.first(), longer = width == MAX_BRIDGED_RIVER)
    }

    private fun restsOn(cell: GridCell): Boolean =
        standing[cell]?.isGroundCover != false && floors[cell]?.isSoil != true && map.isBuildable(cell.x, cell.y)

    /**
     * Whether something of [kind] with [footprint] fits with its first cell at
     * (originX, originY). Every cell must be inside the grid and not river, and its layer must
     * be free: paving only needs the floor layer, so it can go under something already standing
     * there, and anything can be stood on paving. Given its [objectId], a fence may also go onto
     * a lawn and a lawn under a fence.
     */
    fun checkPlacement(
        footprint: Footprint,
        originX: Int,
        originY: Int,
        kind: PlacementKind = PlacementKind.OBJECT,
        objectId: String? = null,
    ): PlacementCheck {
        val cells = footprint.cells(originX, originY)
        return when {
            cells.any { !map.isInside(it.x, it.y) } -> PlacementCheck.OUT_OF_BOUNDS
            cells.any { !map.isBuildable(it.x, it.y) } -> PlacementCheck.NOT_BUILDABLE
            // Nothing goes on the cells a bridge stands over, the banks at its ends included.
            cells.any { it in bridges } -> PlacementCheck.OCCUPIED
            cells.any { standing[it]?.isTerrain == true } -> PlacementCheck.NOT_BUILDABLE
            // Nor is anything laid under or stood in a pond, though it can be taken away again.
            cells.any { standing[it]?.isPond == true } -> PlacementCheck.OCCUPIED
            kind != PlacementKind.OBJECT && cells.any { it in floors } -> PlacementCheck.OCCUPIED
            kind != PlacementKind.FLOOR && cells.any { taken(it, objectId) } -> PlacementCheck.OCCUPIED
            else -> PlacementCheck.OK
        }
    }

    /** Whether [cell] has no room for [objectId] (null: something that shares with nothing). */
    private fun taken(cell: GridCell, objectId: String?): Boolean {
        val placingLawn = objectId == LawnGrowth.OBJECT_ID
        val placingFence = objectId != null && sharesCellWithLawn(objectId)
        val thing = standing[cell]
        return when {
            thing != null -> !(placingLawn && thing.runKind != null)
            cell in lawns -> !placingFence
            else -> false
        }
    }

    companion object {
        /** The widest river a bridge crosses. */
        const val MAX_BRIDGED_RIVER = 2

        /** Whether [objectId] can share its cell with a lawn: a fence (#55) or the edging. */
        fun sharesCellWithLawn(objectId: String): Boolean = RunKind.of(objectId) != null
    }
}
