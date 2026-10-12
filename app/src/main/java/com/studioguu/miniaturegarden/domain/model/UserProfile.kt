package com.studioguu.miniaturegarden.domain.model

data class UserProfile(
    val coins: Long = 0L,
    val playerLevel: Int = 1,
    /** Total experience earned; [playerLevel] is derived from it via [LevelTable]. */
    val currentXp: Long = 0L,
    val inventoryCapacity: Int = DEFAULT_INVENTORY_CAPACITY,
    /** Wall clock (epoch millis) at the last save. */
    val lastUpdatedTimestamp: Long = 0L,
    /** Monotonic clock (elapsedRealtime millis) at the last save. */
    val lastMonoClockTime: Long = 0L,
    val signature: String = "",
) {
    /** Level and experience in the form progress bars and unlock checks use. */
    val stats: PlayerStats get() = PlayerStats(level = playerLevel, totalXp = currentXp)

    /** Adds experience and raises [playerLevel] to match; a level is never taken away. */
    fun gainXp(amount: Int): UserProfile {
        val xp = currentXp + amount
        return copy(currentXp = xp, playerLevel = maxOf(playerLevel, LevelTable.levelForXp(xp)))
    }

    /**
     * Takes experience back, down to none at most. The level stays: a level is never taken away,
     * and it rises again only once the experience has been earned back.
     */
    fun loseXp(amount: Int): UserProfile = copy(currentXp = (currentXp - amount).coerceAtLeast(0L))

    companion object {
        const val DEFAULT_INVENTORY_CAPACITY = 16

        /** What a new player starts with: enough to buy and plant a first few seeds. */
        const val STARTING_COINS = 100L
    }
}
