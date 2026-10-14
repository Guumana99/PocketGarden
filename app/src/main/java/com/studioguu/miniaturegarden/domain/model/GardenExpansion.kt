package com.studioguu.miniaturegarden.domain.model

/** Whether the garden can grow to its next size now, and if not, what is missing. */
enum class ExpansionCheck {
    READY,

    /** It is as large as it gets. */
    AT_MAX_SIZE,

    /** A finished symbol building has to stand in the garden first. */
    NEEDS_SYMBOL,

    /** The player's level is too low for the next size. */
    NEEDS_LEVEL,
}

/**
 * What it takes to grow the garden to one size.
 *
 * @property needsSymbol a finished symbol building ([StructureData.isSymbol]) has to stand in the garden.
 * @property playerLevel the level the player needs.
 */
data class ExpansionStage(val size: Int, val needsSymbol: Boolean, val playerLevel: Int)

/**
 * How the garden grows. It starts at 9 x 9 and grows a ring of new lawn all the way round at a
 * time: 9 -> 11 -> 13. Growing keeps the garden centred, so everything in it moves one cell
 * along each axis ([RING]); nothing is changed otherwise, and new cells are plain lawn.
 *
 *     size    needs
 *     11x11   a finished symbol building
 *     13x13   a finished symbol building and Lv.8
 *
 * Growing is the player's choice, made from the symbol building's panel: it never happens by itself.
 */
object GardenExpansion {
    val STAGES: List<ExpansionStage> = listOf(
        ExpansionStage(size = 11, needsSymbol = true, playerLevel = 1),
        ExpansionStage(size = 13, needsSymbol = true, playerLevel = 8),
    )

    val MAX_SIZE: Int = STAGES.last().size

    /** How far every object moves along each axis when the garden grows by one stage. */
    const val RING = 1

    /** The stage after [currentSize], or null when the garden is as large as it gets. */
    fun nextStage(currentSize: Int): ExpansionStage? = STAGES.firstOrNull { it.size > currentSize }

    /** Whether a garden of [currentSize] holding [objects], kept by a player at [playerLevel], can grow now. */
    fun check(currentSize: Int, objects: List<GridObject>, playerLevel: Int): ExpansionCheck {
        val stage = nextStage(currentSize) ?: return ExpansionCheck.AT_MAX_SIZE
        return when {
            stage.needsSymbol && objects.none { it.isSymbol && !it.isWorking } -> ExpansionCheck.NEEDS_SYMBOL
            playerLevel < stage.playerLevel -> ExpansionCheck.NEEDS_LEVEL
            else -> ExpansionCheck.READY
        }
    }

    /** [obj] where it ends up once the garden has grown by one stage. */
    fun shifted(obj: GridObject): GridObject = obj.copy(gridX = obj.gridX + RING, gridY = obj.gridY + RING)
}
