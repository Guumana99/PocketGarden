package com.studioguu.miniaturegarden.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * The practice garden of the first steps. While it is on, everything that goes through
 * [GardenRepository] finds a new garden held in memory instead of the saved one: the same screens
 * and rules, with nothing of the player's at stake. Leaving forgets it, coins and experience and
 * all, and the saved garden is there again as it was, however often the first steps are played.
 */
interface PracticeMode {
    val practising: StateFlow<Boolean>

    /** Starts a practice garden; one already under way is started over. */
    fun enter()

    fun leave()
}
