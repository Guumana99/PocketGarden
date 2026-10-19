package com.studioguu.miniaturegarden.domain.model

/**
 * The sprinkler: not a tool in the hand but a thing put down in the garden, like a bench. While
 * it is switched on ([GridObject.isOn], a tap on it in its panel) it waters the beds round it,
 * whose soil then dries out far more slowly; switched off, it is only an ornament.
 */
object Sprinkler {
    const val OBJECT_ID = "deco_sprinkler"

    /** It reaches the ring of cells round it, diagonals included. */
    const val RANGE = 1

    /** The soil of a plant in reach of a running sprinkler dries this many times as fast. */
    const val MOISTURE_DRAIN_FACTOR = 0.25f

    /** The cells a sprinkler at ([gridX], [gridY]) waters, without its own. */
    fun cellsInReach(gridX: Int, gridY: Int): Set<GridCell> {
        val reach = HashSet<GridCell>()
        for (dx in -RANGE..RANGE) for (dy in -RANGE..RANGE) {
            if (dx != 0 || dy != 0) reach += GridCell(gridX + dx, gridY + dy)
        }
        return reach
    }

    /** Whether [plant] is watered by a running sprinkler in [garden]. */
    fun waters(plant: GridObject, garden: List<GridObject>): Boolean {
        val here = GridCell(plant.gridX, plant.gridY)
        return garden.any { it.isSprinkler && it.isOn && !it.isWorking && here in cellsInReach(it.gridX, it.gridY) }
    }
}
