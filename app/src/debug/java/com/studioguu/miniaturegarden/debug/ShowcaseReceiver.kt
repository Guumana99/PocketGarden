package com.studioguu.miniaturegarden.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GardenView
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.TimeOfDay
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.repository.GameClock
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.ui.debug.Showcase
import com.studioguu.miniaturegarden.ui.debug.ShowcaseControls
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sets up the garden for promotional pictures, from adb (debug builds only; docs/promo/README.md):
 *
 *     adb shell am broadcast -a com.studioguu.miniaturegarden.debug.SHOWCASE -p com.studioguu.miniaturegarden \
 *         --ez load true                      # replaces the saved garden with [ShowcaseGarden]
 *         --es season AUTUMN --es time DAY --es weather SUNNY --ez hide_ui true \
 *         --es creatures none                 # none, all, or kinds such as butterfly,bird
 *         --ef scale 0.32 --ef pan_x 0 --ef pan_y 0 \
 *         --ez butterfly true                 # one comes now to ShowcaseGarden.BUTTERFLY_FLOWER
 *
 * Every broadcast but a bare load sets the whole look afresh: what it leaves out goes back to the
 * garden's own. `--ez clear true` takes the look away altogether.
 */
@AndroidEntryPoint
class ShowcaseReceiver : BroadcastReceiver() {
    @Inject lateinit var gardenRepository: GardenRepository
    @Inject lateinit var clock: GameClock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_CLEAR, false)) {
            ShowcaseControls.set(null)
            Log.i(TAG, "showcase cleared")
            return
        }
        val load = intent.getBooleanExtra(EXTRA_LOAD, false)
        if (load) {
            val pending = goAsync()
            scope.launch {
                try {
                    loadGarden()
                    Log.i(TAG, "showcase garden loaded")
                } finally {
                    pending.finish()
                }
            }
        }
        val keys = intent.extras?.keySet().orEmpty() - setOf(EXTRA_LOAD)
        if (keys.isEmpty()) return
        val showcase = showcaseOf(intent)
        ShowcaseControls.set(showcase)
        Log.i(TAG, "showcase set: $showcase")
    }

    private suspend fun loadGarden() {
        val now = clock.now()
        gardenRepository.deleteSavedGarden()
        gardenRepository.ensureInitialized()
        gardenRepository.runInTransaction {
            gardenRepository.getGridObjects().forEach { gardenRepository.deleteGridObject(it.instanceId) }
            gardenRepository.upsertGridObjects(ShowcaseGarden.objects(now))
            gardenRepository.saveProgress(
                GardenProgress(
                    season = Season.AUTUMN,
                    tutorialStep = TutorialStep.DONE,
                    gardenSize = ShowcaseGarden.SIZE,
                    seasonStartedAt = now,
                ),
            )
            val profile = gardenRepository.getUserProfile() ?: UserProfile()
            gardenRepository.saveUserProfile(
                profile.copy(coins = 1_200L, playerLevel = PLAYER_LEVEL, currentXp = LevelTable.xpForLevel(PLAYER_LEVEL)),
            )
        }
    }

    private companion object {
        const val TAG = "Showcase"
        const val EXTRA_LOAD = "load"
        const val EXTRA_CLEAR = "clear"

        /** The level the showcase's player has: nothing from later levels is in the garden. */
        const val PLAYER_LEVEL = 5

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun showcaseOf(intent: Intent): Showcase {
            val scale = intent.getFloatExtra("scale", 0f)
            return Showcase(
                season = intent.getStringExtra("season")?.let { name -> Season.entries.first { it.name == name } },
                timeOfDay = intent.getStringExtra("time")?.let { name -> TimeOfDay.entries.first { it.name == name } },
                weather = intent.getStringExtra("weather")?.let { name -> WeatherType.entries.first { it.name == name } },
                hideUi = intent.getBooleanExtra("hide_ui", false),
                creatures = when (val kinds = intent.getStringExtra("creatures")) {
                    null, "all" -> null
                    "none" -> emptySet()
                    else -> kinds.split(',').map { id -> Creature.entries.first { it.id == id.trim() } }.toSet()
                },
                view = if (scale > 0f) GardenView(scale, intent.getFloatExtra("pan_x", 0f), intent.getFloatExtra("pan_y", 0f)) else null,
                butterfly = if (intent.getBooleanExtra("butterfly", false)) ShowcaseGarden.BUTTERFLY_FLOWER else null,
            )
        }
    }
}
