package com.studioguu.miniaturegarden.domain.repository

import com.studioguu.miniaturegarden.domain.model.RewardType
import com.studioguu.miniaturegarden.domain.model.RewardUsage
import kotlinx.coroutines.flow.Flow

/** Keeps count of the day's reward claims. */
interface RewardLimitRepository {

    val usage: Flow<RewardUsage>

    /**
     * Uses up one of [today]'s claims of [type] and returns true, or returns false and changes
     * nothing when the day's limit has been reached. A new day starts from zero.
     */
    suspend fun tryConsume(type: RewardType, today: Long): Boolean
}
