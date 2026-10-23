package com.studioguu.miniaturegarden.domain.model

/**
 * Experience needed for each player level. Reaching Lv.N takes a cumulative 50 x N x (N - 1) XP,
 * so each level costs 100 more than the one before:
 *
 *     Lv.2 = 100   Lv.3 = 300   Lv.4 = 600   Lv.5 = 1,000   Lv.6 = 1,500   Lv.10 = 4,500
 *
 * The first step is deliberately short. A tulip is worth about 65 XP per two-day cycle (harvest
 * plus waterings), so two harvests reach Lv.2, and the first shop unlocks. Everyone starts at Lv.1 with 0 XP.
 */
object LevelTable {
    const val MAX_LEVEL = 99

    /** Cumulative XP at which [level] is reached. */
    fun xpForLevel(level: Int): Long {
        if (level <= 1) return 0L
        val n = level.coerceAtMost(MAX_LEVEL).toLong()
        return 50L * n * (n - 1)
    }

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
