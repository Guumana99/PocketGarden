package com.studioguu.miniaturegarden.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.RewardType
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
import com.studioguu.miniaturegarden.domain.repository.RewardLimitRepository
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.ui.debug.Showcase
import com.studioguu.miniaturegarden.ui.components.todayEpochDay
import com.studioguu.miniaturegarden.ui.hud.RainCloudState
import com.studioguu.miniaturegarden.ui.debug.PerchPreview
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
 *         --es garden fb                      # ... or with [FeedbackGarden], with load
 *         --es garden till                    # ... or with [ShowcaseGarden] and its daikons' row bare (soil: empty beds plain, good, fluffy)
 *         --es roof blue --es wall cream      # ... with the cottage painted so (BuildingPaint ids), with load
 *         --ez hoe true                       # the hoe in hand: taps till the garden
 *         --es bird struct_well               # a bird comes now to the well (or struct_cottage, onto its roof)
 *         --ez side_only true --ez skip_ad true   # only the buttons down the right edge; the cloud's rain without the ad
 *         --es garden thirsty                 # ... [ShowcaseGarden] with every crop half watered (all glint after a rain), with load
 *         --es garden tulips                  # ... [ShowcaseGarden] with a bed of tulips in bloom (ordinary colours), with load
 *         --es garden soilrow                 # ... [ShowcaseGarden.soilRow], three empty beds of each grade side by side, with load
 *         --es garden birdcrops               # ... [ShowcaseGarden.birdCrops], a well among tomatoes, with load
 *         --es garden fences                  # ... or with [FenceGarden] (or bridges), with load
 *         --es garden roses                   # ... or with [RoseGarden], the rose beside crops of its size
 *         --es garden ground                  # ... or with [GroundGarden] (or fencelook, lawnthings, greenhouse, hakusai, banks, perches, birdwell, wellalone, feed, hut, hutpair, millpair, millcottage, flowers), with load
 *         --es tutorial REVEAL_RAIN           # ... at this step of the first steps, with load
 *         --es season AUTUMN --es time DAY --es weather SUNNY --ez hide_ui true \
 *         --es creatures none                 # none, all, or kinds such as butterfly,bird
 *         --ef clock_hour 22                  # what the device's clock is taken to say
 *         --ez audit_text true                # only checks how all the strings wrap ([TextAudit])
 *         --ez perches true                   # only shows (or hides) where the visitors may settle ([PerchPreview])
 *         --ez ad_ready true --ei bonus_coins 120   # an ad waiting; a coin bonus just paid in the shop
 *         --es cloud PARCHED --ef rain_glint 0.5    # what the rain cloud shows; the crops' glint
 *         --ei use_bonus 5 --ei use_rain 1    # only spends the day's rewards
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
    @Inject lateinit var rewardLimits: RewardLimitRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra("use_bonus") || intent.hasExtra("use_rain")) {
            val pending = goAsync()
            scope.launch {
                try {
                    repeat(intent.getIntExtra("use_bonus", 0)) { rewardLimits.tryConsume(RewardType.COIN_BONUS, todayEpochDay()) }
                    repeat(intent.getIntExtra("use_rain", 0)) { rewardLimits.tryConsume(RewardType.BLESSED_RAIN, todayEpochDay()) }
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.getBooleanExtra("audit_text", false)) {
            val pending = goAsync()
            scope.launch {
                try {
                    Log.i(TAG, "text audit: ${TextAudit.run(context)}")
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.hasExtra(EXTRA_PERCHES)) {
            PerchPreview.set(intent.getBooleanExtra(EXTRA_PERCHES, false))
            return
        }
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
                    loadGarden(
                        intent.getStringExtra(EXTRA_GARDEN),
                        intent.getStringExtra("tutorial")?.let(TutorialStep::valueOf) ?: TutorialStep.DONE,
                        intent.getIntExtra("expand_size", GroundGarden.EXPAND_SIZE),
                        intent.getIntExtra("expand_level", GroundGarden.EXPAND_LEVEL),
                        intent.getIntExtra("expand_coins", GroundGarden.EXPAND_COINS),
                        intent.getStringExtra("roof"),
                        intent.getStringExtra("wall"),
                    )
                    Log.i(TAG, "showcase garden loaded")
                } finally {
                    pending.finish()
                }
            }
        }
        val keys = intent.extras?.keySet().orEmpty() - setOf(EXTRA_LOAD, EXTRA_GARDEN, "tutorial", "roof", "wall")
        if (keys.isEmpty()) return
        val showcase = showcaseOf(intent)
        ShowcaseControls.set(showcase)
        Log.i(TAG, "showcase set: $showcase")
    }

    private suspend fun loadGarden(garden: String?, step: TutorialStep, expandSize: Int, expandLevel: Int, expandCoins: Int, roof: String?, wall: String?) {
        val art = garden == "art"
        val fences = garden == "fences"
        val bridges = garden == "bridges"
        val crops = garden == "crops"
        val roses = garden == "roses"
        val ground = garden == "ground"
        val fenceLook = garden == "fencelook"
        val lawnThings = garden == "lawnthings"
        val heights = garden == "heights"
        val greenhouse = garden == "greenhouse"
        val hakusai = garden == "hakusai"
        val nanohana = garden == "nanohana"
        val bridgePlaces = garden == "bridgeplace"
        val expand = garden == "expand"
        val banks = garden == "banks"
        val perches = garden == "perches"
        val birdWell = garden == "birdwell"
        val wellAlone = garden == "wellalone"
        val hungry = garden == "feed"
        val hutAlone = garden == "hut"
        val hutPair = garden == "hutpair"
        val millPair = garden == "millpair"
        val millCottage = garden == "millcottage"
        val flowerBeds = garden == "flowers"
        val feedback = garden == "fb" || banks || perches || birdWell || wellAlone || hungry || hutAlone || hutPair || millPair || millCottage || flowerBeds || art || fences || bridges || crops || roses || ground || fenceLook || lawnThings || heights || greenhouse || hakusai || nanohana || bridgePlaces || expand
        val now = clock.now()
        gardenRepository.deleteSavedGarden()
        gardenRepository.ensureInitialized()
        gardenRepository.runInTransaction {
            gardenRepository.getGridObjects().forEach { gardenRepository.deleteGridObject(it.instanceId) }
            gardenRepository.upsertGridObjects(
                when {
                    art -> ArtGarden.objects(now)
                    fences -> FenceGarden.fences(now)
                    bridges -> FenceGarden.bridges(now)
                    crops -> CropGarden.objects(now)
                    roses -> RoseGarden.objects(now)
                    ground -> GroundGarden.ground(now)
                    banks -> GroundGarden.banks(now)
                    perches -> GroundGarden.perches(now)
                    birdWell -> GroundGarden.birdWell(now)
                    wellAlone -> GroundGarden.birdWell(now, crops = false)
                    hungry -> GroundGarden.hungry(now)
                    hutAlone -> GroundGarden.hutAlone(now)
                    hutPair -> GroundGarden.hutPair(now)
                    millPair -> GroundGarden.millPair(now)
                    millCottage -> GroundGarden.millCottage(now)
                    flowerBeds -> GroundGarden.flowerBeds(now)
                    fenceLook -> GroundGarden.fencesOnGrounds(now)
                    lawnThings -> GroundGarden.lawnThings(now)
                    heights -> GroundGarden.heights(now)
                    greenhouse -> GroundGarden.greenhouse(now)
                    hakusai -> GroundGarden.field(now, PlantSpecies.HAKUSAI)
                    nanohana -> GroundGarden.field(now, PlantSpecies.NANOHANA, young = 0.5f)
                    bridgePlaces -> FenceGarden.bridgePlaces(now)
                    expand -> FeedbackGarden.objects(now) + GroundGarden.joinedUp(now)
                    feedback -> FeedbackGarden.objects(now)
                    garden == "birdcrops" -> ShowcaseGarden.birdCrops(now)
                    garden == "soilrow" -> ShowcaseGarden.soilRow(now)
                    garden == "thirsty" -> ShowcaseGarden.objects(now, thirsty = true, roof = roof, wall = wall)
                    garden == "tulips" -> ShowcaseGarden.objects(now, tulipBed = true, roof = roof, wall = wall)
                    garden == "till" -> ShowcaseGarden.objects(now, bare = ShowcaseGarden.OPEN_ROW, roof = roof, wall = wall)
                    garden == "soil" -> ShowcaseGarden.objects(now, emptyBeds = ShowcaseGarden.OPEN_ROW.mapIndexed { i, cell -> cell to i }.toMap(), roof = roof, wall = wall)
                    else -> ShowcaseGarden.objects(now, roof = roof, wall = wall)
                },
            )
            gardenRepository.saveProgress(
                GardenProgress(
                    season = Season.AUTUMN,
                    tutorialStep = step,
                    gardenSize = if (expand) expandSize else if (feedback) FeedbackGarden.SIZE else ShowcaseGarden.SIZE,
                    seasonStartedAt = now,
                ),
            )
            // The feedback garden's book has every page filled in, so that its pictures can be looked at.
            if (feedback) {
                CollectionEntry.entries.forEach { gardenRepository.upsertCollectionRecord(CollectionRecord(it.objectId, 1, now, revealed = true)) }
                // Some colours of the tulip grown and some not, for its page to show both.
                listOf("red", "pink", "yellow").forEach { gardenRepository.upsertCollectionRecord(CollectionRecord(CollectionEntry.colourRecordId(CollectionEntry.TULIP.objectId, it), 1, now, revealed = true)) }
            }
            if (bridgePlaces) gardenRepository.upsertInventorySlot(InventorySlot(0, GardenItem.STRUCT_BRIDGE.itemId, 5))
            val profile = gardenRepository.getUserProfile() ?: UserProfile()
            val level = if (expand) expandLevel else if (feedback) FeedbackGarden.PLAYER_LEVEL else PLAYER_LEVEL
            gardenRepository.saveUserProfile(
                profile.copy(coins = if (expand) expandCoins.toLong() else 1_200L, playerLevel = level, currentXp = LevelTable.xpForLevel(level)),
            )
        }
    }

    private companion object {
        const val TAG = "Showcase"
        const val EXTRA_LOAD = "load"
        const val EXTRA_CLEAR = "clear"
        const val EXTRA_GARDEN = "garden"
        const val EXTRA_PERCHES = "perches"

        /** The level the showcase's player has: nothing from later levels is in the garden. */
        const val PLAYER_LEVEL = 5

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun showcaseOf(intent: Intent): Showcase {
            val scale = intent.getFloatExtra("scale", 0f)
            return Showcase(
                season = intent.getStringExtra("season")?.let { name -> Season.entries.first { it.name == name } },
                timeOfDay = intent.getStringExtra("time")?.let { name -> TimeOfDay.entries.first { it.name == name } },
                clockHour = intent.takeIf { it.hasExtra("clock_hour") }?.getFloatExtra("clock_hour", 0f),
                weather = intent.getStringExtra("weather")?.let { name -> WeatherType.entries.first { it.name == name } },
                hideUi = intent.getBooleanExtra("hide_ui", false),
                creatures = when (val kinds = intent.getStringExtra("creatures")) {
                    null, "all" -> null
                    "none" -> emptySet()
                    else -> kinds.split(',').map { id -> Creature.entries.first { it.id == id.trim() } }.toSet()
                },
                view = if (scale > 0f) GardenView(scale, intent.getFloatExtra("pan_x", 0f), intent.getFloatExtra("pan_y", 0f)) else null,
                butterfly = if (intent.getBooleanExtra("butterfly", false)) ShowcaseGarden.BUTTERFLY_FLOWER else null,
                adReady = intent.takeIf { it.hasExtra("ad_ready") }?.getBooleanExtra("ad_ready", false),
                bonusCoins = intent.takeIf { it.hasExtra("bonus_coins") }?.getIntExtra("bonus_coins", 0),
                cloud = intent.getStringExtra("cloud")?.let(RainCloudState::valueOf),
                rainGlint = intent.takeIf { it.hasExtra("rain_glint") }?.getFloatExtra("rain_glint", 0f),
                hoe = intent.getBooleanExtra("hoe", false),
                bird = intent.getStringExtra("bird"),
                sideOnly = intent.getBooleanExtra("side_only", false),
                skipAd = intent.getBooleanExtra("skip_ad", false),
            )
        }
    }
}
