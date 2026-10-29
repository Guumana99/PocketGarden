package com.studioguu.miniaturegarden.domain.model

/** Whether the garden can grow to its next size now, and if not, what is missing. */
enum class ExpansionCheck {
    READY,

    /** It is as large as it gets for now. */
    AT_MAX_SIZE,

    /** The player's level is too low for the next size. */
    NEEDS_LEVEL,

    /** The level is there, but not the coins. */
    NEEDS_COINS,
}

/**
 * What it takes to grow the garden to one size: the player's level, and the price in coins.
 *
 * @property open false for a size that is planned (its level and price are set) but cannot be
 *   grown to yet: the camera and drawing are not ready for it.
 */
data class ExpansionStage(val size: Int, val playerLevel: Int, val price: Int, val open: Boolean = true)

/**
 * How the garden grows. A new garden starts at 11 x 11 ([GridObject.GRID_SIZE]) and grows a ring
 * of new lawn all the way round at a time: 11 -> 13. Gardens saved while 9 x 9 was the start
 * ([GridObject.LEGACY_GRID_SIZE]) grow 9 -> 11 -> 13. Growing keeps the garden centred, so everything in it moves one cell
 * along each axis ([RING]); nothing is changed otherwise, and new cells are plain lawn.
 *
 *     size    level   price    note
 *     11x11   Lv.1        0    only for gardens begun at 9 x 9, which were promised it
 *     13x13   Lv.8    3,000
 *     15x15   Lv.14  10,000    planned: not open yet
 *     17x17   Lv.20  20,000    planned: not open yet
 *
 * It takes the level and the coins, nothing else. The prices are a first guess and live here
 * only ([STAGES]).
 *
 * Growing is the player's choice, made in the carpenter's workshop: it never happens by itself.
 * So that the player knows when it can, a letter comes once for each size when the level for it
 * is reached ([PostOffice.gardenCanGrow]).
 */
object GardenExpansion {
    val STAGES: List<ExpansionStage> = listOf(
        ExpansionStage(size = 11, playerLevel = 1, price = 0),
        ExpansionStage(size = 13, playerLevel = 8, price = 3_000),
        ExpansionStage(size = 15, playerLevel = 14, price = 10_000, open = false),
        ExpansionStage(size = 17, playerLevel = 20, price = 20_000, open = false),
    )

    /** The largest the garden can grow to now. */
    val MAX_SIZE: Int = STAGES.last { it.open }.size

    /** How far every object moves along each axis when the garden grows by one stage. */
    const val RING = 1

    /** The stage after [currentSize] that can be grown to, or null when the garden is as large as it gets. */
    fun nextStage(currentSize: Int): ExpansionStage? = STAGES.firstOrNull { it.open && it.size > currentSize }

    /** Whether a garden of [currentSize], kept by a player at [playerLevel] with [coins], can grow now. */
    fun check(currentSize: Int, playerLevel: Int, coins: Long): ExpansionCheck {
        val stage = nextStage(currentSize) ?: return ExpansionCheck.AT_MAX_SIZE
        return when {
            playerLevel < stage.playerLevel -> ExpansionCheck.NEEDS_LEVEL
            coins < stage.price -> ExpansionCheck.NEEDS_COINS
            else -> ExpansionCheck.READY
        }
    }

    /** Whether the player's level is enough for the next size, whatever the coins. */
    fun levelReached(currentSize: Int, playerLevel: Int): Boolean =
        nextStage(currentSize)?.let { playerLevel >= it.playerLevel } ?: false

    /** [obj] where it ends up once the garden has grown by one stage. */
    fun shifted(obj: GridObject): GridObject = obj.copy(gridX = obj.gridX + RING, gridY = obj.gridY + RING)
}
