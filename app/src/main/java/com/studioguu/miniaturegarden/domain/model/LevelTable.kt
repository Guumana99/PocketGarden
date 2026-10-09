package com.studioguu.miniaturegarden.domain.model

import kotlin.math.pow

/**
 * Experience needed for each player level: reaching Lv.N takes a cumulative
 * 100 x N^1.5 XP (Lv.2 = 282, Lv.3 = 519, Lv.4 = 800 ...). Everyone starts at Lv.1 with 0 XP.
 */
object LevelTable {
    const val MAX_LEVEL = 99

    /** Cumulative XP at which [level] is reached. */
    fun xpForLevel(level: Int): Long =
        if (level <= 1) 0L else (100.0 * level.coerceAtMost(MAX_LEVEL).toDouble().pow(1.5)).toLong()

    /** The level a player with [xp] total experience is at. */
    fun levelForXp(xp: Long): Int {
        var level = 1
        while (level < MAX_LEVEL && xp >= xpForLevel(level + 1)) level++
        return level
    }
}
