package com.studioguu.miniaturegarden.data.reward

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.studioguu.miniaturegarden.domain.model.RewardType
import com.studioguu.miniaturegarden.domain.model.RewardUsage
import com.studioguu.miniaturegarden.domain.repository.RewardLimitRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** The day's reward claims, kept in their own Preferences DataStore file. */
@Singleton
class RewardLimitRepositoryImpl @Inject constructor(
    @param:Named(DATA_STORE_NAME) private val dataStore: DataStore<Preferences>,
) : RewardLimitRepository {

    override val usage: Flow<RewardUsage> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::read)

    override suspend fun tryConsume(type: RewardType, today: Long): Boolean {
        var consumed = false
        // edit() runs one at a time, so the check and the increment cannot be split by another claim.
        dataStore.edit { prefs ->
            val current = read(prefs)
            val usedToday = current.usedOn(today, type)
            if (usedToday >= type.dailyLimit) return@edit
            if (current.epochDay != today) {
                // First claim of a new day: yesterday's counts are dropped.
                RewardType.entries.forEach { prefs.remove(countKey(it)) }
                prefs[EPOCH_DAY] = today
            }
            prefs[countKey(type)] = usedToday + 1
            consumed = true
        }
        return consumed
    }

    private fun read(prefs: Preferences): RewardUsage = RewardUsage(
        epochDay = prefs[EPOCH_DAY] ?: 0L,
        used = RewardType.entries.associateWith { prefs[countKey(it)] ?: 0 },
    )

    companion object {
        const val DATA_STORE_NAME = "rewards"

        private val EPOCH_DAY = longPreferencesKey("epoch_day")
        private fun countKey(type: RewardType) = intPreferencesKey("used_${type.name.lowercase()}")
    }
}
