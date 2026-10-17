package com.studioguu.miniaturegarden.domain.model

/**
 * How many cells an object covers: [width] along gridX and [height] along gridY. An object's
 * (gridX, gridY) is the cell with the smallest x and y; the rest extend towards larger values.
 */
data class Footprint(val width: Int, val height: Int) {
    init {
        require(width >= 1 && height >= 1) { "A footprint must cover at least one cell, was ${width}x$height" }
    }

    /** Every cell covered when the footprint's first cell is (originX, originY). */
    fun cells(originX: Int, originY: Int): List<GridCell> =
        (0 until height).flatMap { dy -> (0 until width).map { dx -> GridCell(originX + dx, originY + dy) } }

    /**
     * The first cell to give this footprint when the player taps (tapX, tapY) in a garden of
     * [gardenSize] cells each way. The footprint is centred on the tapped cell (for an even span the
     * tapped cell is the back one of the middle two), then slid back
     * inside the garden if that would leave part of it outside: a tap anywhere inside the garden
     * always names a spot the whole footprint fits in, so a building can stand right against
     * the edge. A single cell is placed on the tapped cell itself.
     */
    fun originFor(tapX: Int, tapY: Int, gardenSize: Int): GridCell {
        fun axis(tap: Int, span: Int): Int {
            val centred = tap - (span - 1) / 2
            return if (span > gardenSize) centred else centred.coerceIn(0, gardenSize - span)
        }
        return GridCell(axis(tapX, width), axis(tapY, height))
    }

    companion object {
        val SINGLE = Footprint(1, 1)

        /** Footprint of the object with this id; anything that is not a [StructureData] covers one cell. */
        fun forObjectId(objectId: String): Footprint = StructureData.fromObjectId(objectId)?.footprint ?: SINGLE
    }
}

/**
 * Buildings and other objects larger than one cell.
 *
 * @property heightSteps how tall it stands, roof included, in height steps (a quarter of a
 *   tile's height on screen). What it may hide behind it is worked out from this.
 * @property isSymbol a symbol building: the garden's landmark. A garden has one at most, and
 *   having one is what lets the garden grow (see [GardenExpansion]).
 */
enum class StructureData(
    val objectId: String,
    val footprint: Footprint,
    val heightSteps: Float,
    val isSymbol: Boolean = false,
) {
    HUT("struct_hut", Footprint(width = 2, height = 2), heightSteps = 7f),
    WELL("struct_well", Footprint(width = 1, height = 2), heightSteps = 2.4f),

    /**
     * A cottage with a chimney: the first symbol building. As tall as its picture (#40): the
     * chimney top stands 16 steps up.
     */
    COTTAGE("struct_cottage", Footprint(width = 3, height = 3), heightSteps = 16f, isSymbol = true),

    /**
     * A wooden windmill whose sails turn in the wind, pumping water for the beds round it
     * ([StructureEffect.WINDMILL]). The roof is 18 steps up and the sails reach a little higher
     * as they go round.
     */
    WINDMILL("struct_windmill", Footprint(width = 2, height = 2), heightSteps = 20f);

    companion object {
        fun fromObjectId(objectId: String): StructureData? = entries.firstOrNull { it.objectId == objectId }

        /** Whether [objectId] is a symbol building. */
        fun isSymbol(objectId: String): Boolean = fromObjectId(objectId)?.isSymbol == true
    }
}
