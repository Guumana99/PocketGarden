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

    companion object {
        val SINGLE = Footprint(1, 1)

        /** Footprint of the object with this id; anything that is not a [StructureData] covers one cell. */
        fun forObjectId(objectId: String): Footprint = StructureData.fromObjectId(objectId)?.footprint ?: SINGLE
    }
}

/** Buildings and other objects larger than one cell. */
enum class StructureData(val objectId: String, val footprint: Footprint) {
    HUT("struct_hut", Footprint(width = 2, height = 2)),
    WELL("struct_well", Footprint(width = 1, height = 2));

    companion object {
        fun fromObjectId(objectId: String): StructureData? = entries.firstOrNull { it.objectId == objectId }
    }
}
