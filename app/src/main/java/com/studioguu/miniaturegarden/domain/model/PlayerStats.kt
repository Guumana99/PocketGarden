package com.studioguu.miniaturegarden.domain.model

/**
 * A player's progression: level and experience, with the figures a progress bar or an unlock
 * check needs. Derived from [UserProfile]; nothing here is stored separately.
 */
data class PlayerStats(
    val level: Int,
    val totalXp: Long,
    /** Pages of the encyclopedia the player has filled in. Not part of the profile: see [UnlockCondition.CollectionPages]. */
    val collectionPages: Int = 0,
) {
    val isMaxLevel: Boolean get() = level >= LevelTable.MAX_LEVEL

    /** XP earned since reaching the current level. */
    val xpIntoLevel: Long get() = (totalXp - LevelTable.xpForLevel(level)).coerceAtLeast(0L)

    /** XP between the current level and the next one; 0 at the level cap. */
    val xpSpanOfLevel: Long
        get() = if (isMaxLevel) 0L else LevelTable.xpForLevel(level + 1) - LevelTable.xpForLevel(level)

    /** XP still missing for the next level; 0 at the level cap. */
    val xpToNextLevel: Long get() = (xpSpanOfLevel - xpIntoLevel).coerceAtLeast(0L)

    /** 0.0 .. 1.0 through the current level; 1.0 at the level cap. */
    val progress: Float
        get() = if (isMaxLevel) 1f else (xpIntoLevel.toFloat() / xpSpanOfLevel).coerceIn(0f, 1f)
}

/** What a player must have achieved before an item appears for sale. */
sealed interface UnlockCondition {
    fun isMetBy(stats: PlayerStats): Boolean

    /** Available from the start. */
    data object None : UnlockCondition {
        override fun isMetBy(stats: PlayerStats) = true
    }

    /** Available once the player has reached [level]. */
    data class PlayerLevel(val level: Int) : UnlockCondition {
        override fun isMetBy(stats: PlayerStats) = stats.level >= level
    }

    /** Available once the player has filled in [pages] pages of the encyclopedia. */
    data class CollectionPages(val pages: Int) : UnlockCondition {
        override fun isMetBy(stats: PlayerStats) = stats.collectionPages >= pages
    }
}
