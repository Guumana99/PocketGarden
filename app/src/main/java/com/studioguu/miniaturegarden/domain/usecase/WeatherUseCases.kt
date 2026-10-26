package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GameDay
import com.studioguu.miniaturegarden.domain.model.SeasonClock
import com.studioguu.miniaturegarden.domain.model.WeatherDiary
import com.studioguu.miniaturegarden.domain.model.WeatherPlanner
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random

/**
 * Keeps the garden's weather diary: every day from [KEEP_DAYS] days back (as far as time away
 * is ever counted) up to tomorrow has its weather decided and stored. A day is decided once
 * ([WeatherPlanner], from the garden's own seed and the season and phase that day falls in) and
 * never again, so today's weather stays what it was, and tomorrow's is known a day ahead and
 * always comes true. Days older than that are let go.
 *
 * A garden without a seed (a new one, or one saved before the weather was its own) is given one.
 */
class KeepWeatherUseCase(
    private val gardenRepository: GardenRepository,
    private val newSeed: () -> Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    @Inject constructor(gardenRepository: GardenRepository) : this(gardenRepository, { Random.nextLong() })

    /** Brings the diary up to [now] and returns it. Empty while there is no garden. */
    suspend operator fun invoke(now: Long): WeatherDiary = gardenRepository.runInTransaction {
        val progress = gardenRepository.getProgress() ?: return@runInTransaction WeatherDiary.EMPTY
        val seed = progress.weatherSeed.takeIf { it != 0L }
            ?: generateSequence(newSeed).first { it != 0L }.also { gardenRepository.saveProgress(progress.copy(weatherSeed = it)) }
        // A season clock that has not started yet starts today (see AdvanceSeasonUseCase).
        val startedAt = progress.seasonStartedAt.takeIf { it > 0L } ?: now
        val today = GameDay.of(now, zone)
        val first = today - KEEP_DAYS
        val days = gardenRepository.getWeatherDays().filterKeys { it >= first - WeatherPlanner.MAX_RUN }.toMutableMap()
        val decided = HashMap<Long, WeatherType>()
        for (day in first..today + 1) {
            if (day in days) continue
            val (season, phase) = SeasonClock.on(day, progress.season, startedAt, zone)
            // Only an unbroken run of days just before counts against a fourth in a row.
            val previous = (WeatherPlanner.MAX_RUN downTo 1).map { days[day - it] }
            val weather = WeatherPlanner.decide(seed, day, season, phase, if (null in previous) emptyList() else previous.filterNotNull())
            days[day] = weather
            decided[day] = weather
        }
        if (decided.isNotEmpty()) gardenRepository.keepWeatherDays(decided)
        // The few days before the window stay, for the rule against long runs at its start.
        gardenRepository.forgetWeatherBefore(first - WeatherPlanner.MAX_RUN)
        WeatherDiary(days, zone)
    }

    companion object {
        /** How many days back the diary reaches: as far as time away is counted, and a day over. */
        const val KEEP_DAYS = CalculateOfflineProgressUseCase.MAX_OFFLINE_MILLIS / SeasonClock.DAY_MILLIS + 1
    }
}
