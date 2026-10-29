package com.studioguu.miniaturegarden.domain.model

/**
 * Experience needed for each player level. [STEPS] is what each level costs on its own, quick at
 * first and slower as it goes, so that a player who looks in three times a day and never watches
 * an ad comes to Lv.20 in about three of the garden's years (108 days):
 *
 *     Lv.2 = 80   Lv.3 = 380   Lv.4 = 1,080   Lv.5 = 2,580   Lv.10 = 21,080   Lv.20 = 113,280
 *
 * From Lv.21 on every level costs the same as the last step ([STEPS].last()). Watering is still
 * 5 XP a time. Everyone starts at Lv.1 with 0 XP.
 */
object LevelTable {
    const val MAX_LEVEL = 99

    /** XP from Lv.N to Lv.N+1, for N = 1 .. 19; each level after that costs the last of these. */
    val STEPS: List<Long> = listOf(
        80, 300, 700, 1_500, 2_200, 2_900, 3_600, 4_500, 5_300,
        6_000, 6_600, 7_200, 7_900, 8_800, 9_500, 10_200, 11_000, 12_000, 13_000,
    )

    private val CUMULATIVE: LongArray = LongArray(MAX_LEVEL + 1).also { xp ->
        for (level in 2..MAX_LEVEL) xp[level] = xp[level - 1] + STEPS.getOrElse(level - 2) { STEPS.last() }
    }

    /** Cumulative XP at which [level] is reached. */
    fun xpForLevel(level: Int): Long = if (level <= 1) 0L else CUMULATIVE[level.coerceAtMost(MAX_LEVEL)]

    /**
     * How far a player with [xp] total experience is through [level], 0 (just reached) .. 1 (the
     * next level). Full at [MAX_LEVEL], where there is no next level.
     */
    fun progressInLevel(xp: Long, level: Int): Float {
        if (level >= MAX_LEVEL) return 1f
        val from = xpForLevel(level)
        val span = xpForLevel(level + 1) - from
        if (span <= 0L) return 1f
        return ((xp - from).toFloat() / span).coerceIn(0f, 1f)
    }

    /** The level a player with [xp] total experience is at. */
    fun levelForXp(xp: Long): Int {
        var level = 1
        while (level < MAX_LEVEL && xp >= xpForLevel(level + 1)) level++
        return level
    }
}

/**
 * A level-up the player has yet to see: from [fromLevel] to [toLevel]. It stays on screen as a
 * board until the player taps OK, so it cannot pass by unnoticed like a floating message.
 */
data class LevelUpNotice(val fromLevel: Int, val toLevel: Int) {
    /** What the new level put in the shop; the board only speaks of new items when there are some. */
    val unlockedItems: List<GardenItem> get() = GardenItem.unlockedBetween(fromLevel, toLevel)

    companion object {
        /**
         * The notice to show after the level went from [previous] to [level], given the one
         * still [pending] on screen, if any. Levels gained while a board is up join it, so the
         * player taps OK once and hears of everything that opened since they last looked.
         */
        fun after(pending: LevelUpNotice?, previous: Int, level: Int): LevelUpNotice? =
            if (level <= previous) pending else LevelUpNotice(pending?.fromLevel ?: previous, level)
    }
}
