package com.studioguu.miniaturegarden.domain.model

/** What the ground of one grid cell is made of. */
enum class TileType(val symbol: Char, val isBuildable: Boolean) {
    GRASS('.', isBuildable = true),

    /** Water. Nothing can be planted or built on it, so nothing on it can be watered either. */
    RIVER('~', isBuildable = false);

    companion object {
        fun fromSymbol(symbol: Char): TileType? = entries.firstOrNull { it.symbol == symbol }
    }
}

/** One cell of the grid. */
data class GridCell(val x: Int, val y: Int)

/**
 * The terrain of the garden: one [TileType] per cell of a square grid.
 *
 * The layout is fixed in code for now ([DEFAULT]); it is not stored in the database.
 */
class GardenMap private constructor(
    val size: Int,
    private val tiles: List<TileType>,
) {
    fun isInside(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size

    /** The terrain at (x, y), or null outside the grid. */
    fun tileAt(x: Int, y: Int): TileType? = if (isInside(x, y)) tiles[y * size + x] else null

    fun isBuildable(x: Int, y: Int): Boolean = tileAt(x, y)?.isBuildable == true

    /**
     * True when one of the four cells sharing an edge with (x, y) is [type]. Not used by any rule
     * yet; it is here for effects such as plants next to the river needing less water.
     */
    fun isNextTo(x: Int, y: Int, type: TileType): Boolean =
        NEIGHBOURS.any { (dx, dy) -> tileAt(x + dx, y + dy) == type }

    fun cellsOf(type: TileType): List<GridCell> =
        (0 until size * size).filter { tiles[it] == type }.map { GridCell(it % size, it / size) }

    companion object {
        private val NEIGHBOURS = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)

        /**
         * Builds a map from one string per row (gridY, top to bottom), one character per cell
         * (gridX, left to right): `.` grass, `~` river.
         */
        fun parse(vararg rows: String): GardenMap {
            val size = rows.size
            require(size > 0) { "A map needs at least one row" }
            val tiles = rows.flatMapIndexed { y, row ->
                require(row.length == size) { "Row $y has ${row.length} cells, expected $size" }
                row.map { symbol -> requireNotNull(TileType.fromSymbol(symbol)) { "Unknown tile '$symbol' in row $y" } }
            }
            return GardenMap(size, tiles)
        }

        fun allGrass(size: Int = GridObject.GRID_SIZE): GardenMap =
            GardenMap(size, List(size * size) { TileType.GRASS })

        /** Plain lawn. The river is no longer part of the map: the player lays it tile by tile. */
        val DEFAULT: GardenMap = allGrass()
    }
}
