package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.SeasonClock
import com.studioguu.miniaturegarden.domain.model.after
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import javax.inject.Inject

/**
 * Turns the garden's year to where it stands now ([SeasonClock]) and posts the letters that go
 * with it: one when a season comes, and one a little before it ends, saying which is next.
 * Run on every sync; it changes nothing between those moments.
 *
 * A garden whose clock has not started (a new one, or one saved before there were seasons)
 * starts it now, in the season it is in, with a letter welcoming that season.
 */
class AdvanceSeasonUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    /** @return the season that has just come, if one did. */
    suspend operator fun invoke(now: Long): Season? = gardenRepository.runInTransaction {
        val progress = gardenRepository.getProgress() ?: return@runInTransaction null
        if (progress.seasonStartedAt <= 0L) {
            gardenRepository.saveProgress(progress.copy(seasonStartedAt = now))
            gardenRepository.postLetter(Letter.arrived(progress.season, now))
            return@runInTransaction null
        }
        val state = SeasonClock.at(progress.season, progress.seasonStartedAt, now)
        var arrived: Season? = null
        if (state.startedAt != progress.seasonStartedAt) {
            gardenRepository.saveProgress(progress.copy(season = state.season, seasonStartedAt = state.startedAt))
            // Only the season of now is announced, however many went by while the player was away.
            gardenRepository.postLetter(Letter.arrived(state.season, state.startedAt))
            arrived = state.season
        }
        if (SeasonClock.isEndNear(state.startedAt, now)) {
            gardenRepository.postLetter(Letter.coming(state.season.after(), SeasonClock.endOf(state.startedAt), now))
        }
        arrived
    }

    /**
     * Debug menu: lets the rest of this season go by at once, so that the next one comes now
     * (with its letter), as it would after waiting.
     */
    suspend fun skipSeason(now: Long): Season? {
        gardenRepository.runInTransaction {
            val progress = gardenRepository.getProgress() ?: return@runInTransaction
            val startedAt = if (progress.seasonStartedAt > 0L) progress.seasonStartedAt else now
            // Moved back so that the season ends just now.
            gardenRepository.saveProgress(progress.copy(seasonStartedAt = minOf(startedAt, now - SeasonClock.SEASON_MILLIS)))
        }
        return invoke(now)
    }
}

/** The post: the letters, newest first, and putting them down as read. */
class ReadLettersUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    /** The player has opened the post and seen everything in it. */
    suspend fun markAllRead() = gardenRepository.markLettersRead()
}
