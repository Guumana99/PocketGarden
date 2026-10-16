package com.studioguu.miniaturegarden.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.data.ads.RewardedAdManager
import com.studioguu.miniaturegarden.data.ads.RewardedAdResult
import com.studioguu.miniaturegarden.data.asset.AssetRepository
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TerrainTextures
import com.studioguu.miniaturegarden.data.audio.AudioManager
import com.studioguu.miniaturegarden.data.audio.BgmTrack
import com.studioguu.miniaturegarden.data.audio.SoundEffect
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.CreatureVisits
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenView
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.InventorySort
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.LevelUpNotice
import com.studioguu.miniaturegarden.domain.model.NatureNotes
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.RewardType
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.TutorialGuide
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.repository.RewardLimitRepository
import com.studioguu.miniaturegarden.domain.repository.SettingsRepository
import com.studioguu.miniaturegarden.domain.usecase.ActionResult
import com.studioguu.miniaturegarden.domain.usecase.AdvanceSeasonUseCase
import com.studioguu.miniaturegarden.domain.usecase.PostLettersUseCase
import com.studioguu.miniaturegarden.domain.usecase.ReadLettersUseCase
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase
import com.studioguu.miniaturegarden.domain.usecase.CompleteWorkUseCase
import com.studioguu.miniaturegarden.domain.usecase.ExpandGardenUseCase
import com.studioguu.miniaturegarden.domain.usecase.NoteResult
import com.studioguu.miniaturegarden.domain.usecase.RecordNatureNoteUseCase
import com.studioguu.miniaturegarden.domain.usecase.ScheduleGardenNotificationsUseCase
import com.studioguu.miniaturegarden.domain.usecase.FollowTutorialUseCase
import com.studioguu.miniaturegarden.domain.usecase.GrantRewardUseCase
import com.studioguu.miniaturegarden.domain.usecase.HarvestPlantUseCase
import com.studioguu.miniaturegarden.domain.usecase.HoeUseCase
import com.studioguu.miniaturegarden.domain.usecase.MoveObjectUseCase
import com.studioguu.miniaturegarden.domain.usecase.OpenStarterCrateUseCase
import com.studioguu.miniaturegarden.domain.usecase.RewardResult
import com.studioguu.miniaturegarden.domain.usecase.PlaceItemUseCase
import com.studioguu.miniaturegarden.domain.usecase.RemoveObjectUseCase
import com.studioguu.miniaturegarden.domain.usecase.SkipTimeUseCase
import com.studioguu.miniaturegarden.domain.usecase.StartRemovalUseCase
import com.studioguu.miniaturegarden.domain.usecase.StoreObjectUseCase
import com.studioguu.miniaturegarden.domain.usecase.SyncGardenUseCase
import com.studioguu.miniaturegarden.domain.usecase.UnveilBuildingUseCase
import com.studioguu.miniaturegarden.ui.components.todayEpochDay
import com.studioguu.miniaturegarden.ui.effects.GardenEffect
import com.studioguu.miniaturegarden.ui.render.TileCoord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class GardenViewModel @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val assetRepository: AssetRepository,
    private val syncGarden: SyncGardenUseCase,
    private val careForPlant: CareForPlantUseCase,
    private val placeItem: PlaceItemUseCase,
    private val harvestPlant: HarvestPlantUseCase,
    private val startRemoval: StartRemovalUseCase,
    private val completeWork: CompleteWorkUseCase,
    private val moveObject: MoveObjectUseCase,
    private val unveilBuilding: UnveilBuildingUseCase,
    private val skipTime: SkipTimeUseCase,
    private val hoe: HoeUseCase,
    private val openStarterCrate: OpenStarterCrateUseCase,
    private val followTutorial: FollowTutorialUseCase,
    private val audio: AudioManager,
    private val expandGarden: ExpandGardenUseCase,
    private val scheduleNotifications: ScheduleGardenNotificationsUseCase,
    private val recordNatureNote: RecordNatureNoteUseCase,
    private val advanceSeason: AdvanceSeasonUseCase,
    private val postLetters: PostLettersUseCase,
    private val readLetters: ReadLettersUseCase,
    private val settingsRepository: SettingsRepository,
    private val grantReward: GrantRewardUseCase,
    private val rewardedAds: RewardedAdManager,
    rewardLimits: RewardLimitRepository,
) : ViewModel() {

    val gridObjects: StateFlow<List<GridObject>> = gardenRepository.observeGridObjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profile: StateFlow<UserProfile?> = gardenRepository.observeUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val progress = gardenRepository.observeProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The garden's terrain: lawn, as large as the garden has grown. */
    val gardenMap: StateFlow<GardenMap> = progress.map { it?.gardenSize ?: GridObject.GRID_SIZE }
        .distinctUntilChanged()
        .map { GardenMap.allGrass(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GardenMap.DEFAULT)

    /**
     * The player's first steps; [TutorialStep.DONE] in free play. Null until the garden has been
     * read, so that nothing is shown (or hidden) by a guess in the meantime.
     */
    val tutorialStep: StateFlow<TutorialStep?> = progress.map { it?.tutorialStep }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val season: StateFlow<Season> = progress.map { it?.season ?: Season.SPRING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Season.SPRING)

    /** The post, newest first: every letter that has come, read or not. */
    val letters: StateFlow<List<Letter>> = gardenRepository.observeLetters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The player has opened one letter's page: that one has been read. */
    fun markLetterRead(letterId: String) {
        viewModelScope.launch { readLetters.markRead(letterId) }
    }

    /** Debug menu: the rest of this season goes by at once, and the next one comes. */
    fun debugNextSeason() {
        viewModelScope.launch {
            initJob.join()
            val arrived = actionMutex.withLock { advanceSeason.skipSeason(System.currentTimeMillis()) }
            arrived?.let(::announceSeason)
        }
    }

    /** A season has come while the player is here: said in passing; the letter is in the post. */
    private fun announceSeason(season: Season) {
        audio.play(SoundEffect.LEVEL_UP)
        _seasonArrived.trySend(season)
    }

    private val _seasonArrived = Channel<Season>(Channel.BUFFERED)

    /** Seasons that have just come, for the screen to say so. */
    val seasonArrived: Flow<Season> = _seasonArrived.receiveAsFlow()

    /** The cells the current first step points at with a sparkle. */
    val hintCells: StateFlow<Set<GridCell>> = combine(tutorialStep, gridObjects, gardenMap) { step, objects, map ->
        if (step == null) emptySet() else TutorialGuide.hintCells(step, objects, map)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _openingCrateId = MutableStateFlow<String?>(null)

    /** Non-null while the starter crate's popup is open: the crate it is about. */
    val openingCrate: StateFlow<GridObject?> = combine(gridObjects, _openingCrateId) { objects, id ->
        objects.firstOrNull { it.instanceId == id && it.isStarterCrate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _weather = MutableStateFlow(WeatherType.forTime(System.currentTimeMillis()))
    val weather: StateFlow<WeatherType> = _weather.asStateFlow()

    private var debugWeather: WeatherType? = null

    /** Blessed rains still available today. */
    val rainRemaining: StateFlow<Int> = rewardLimits.usage
        .map { it.remaining(RewardType.BLESSED_RAIN, todayEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val adReady: StateFlow<Boolean> = rewardedAds.isReady

    /**
     * What the book's badge counts: pages of the encyclopedia waiting for the player to fill them
     * in, and creatures newly met whose page in the nature notes has not been looked at yet.
     */
    val collectionReady: StateFlow<Int> = combine(gardenRepository.observeCollection(), gardenRepository.observeNatureNotes()) { pages, notes ->
        CollectionProgress(pages).ready + NatureNotes(notes).unopened
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _blessedRainFalling = MutableStateFlow(false)

    /** True for a few seconds after a blessed rain was granted, while its shower is shown. */
    val blessedRainFalling: StateFlow<Boolean> = _blessedRainFalling.asStateFlow()

    /** Whether there is anything the blessed rain could speed up. */
    fun hasGrowingPlants(): Boolean = gridObjects.value.any {
        it.isPlant && it.status != PlantStatus.WITHERED && it.growthProgress < 1f
    }

    /**
     * Shows the ad and, only if it is watched to the end, lets the blessed rain fall. The plants
     * are sped up as soon as the reward is earned; the shower and the message wait until the ad
     * has closed, so the player sees them.
     */
    fun claimBlessedRain(activity: Activity) {
        var grant: Deferred<RewardResult>? = null
        rewardedAds.show(
            activity,
            onEarned = {
                grant = viewModelScope.async {
                    initJob.join()
                    actionMutex.withLock {
                        val now = System.currentTimeMillis()
                        grantReward.blessedRain(now, currentWeather(now), todayEpochDay())
                    }
                }
            },
            onClosed = { result ->
                when (result) {
                    RewardedAdResult.EARNED -> viewModelScope.launch {
                        if (grant?.await() != RewardResult.GRANTED) {
                            _messages.send(R.string.msg_reward_limit)
                            return@launch
                        }
                        audio.play(SoundEffect.WATER)
                        _messages.send(R.string.msg_blessed_rain_done)
                        _blessedRainFalling.value = true
                        delay(BLESSED_RAIN_SHOWER_MILLIS)
                        _blessedRainFalling.value = false
                    }
                    RewardedAdResult.CANCELLED -> _messages.trySend(R.string.msg_reward_cancelled)
                    RewardedAdResult.UNAVAILABLE -> _messages.trySend(R.string.msg_reward_unavailable)
                }
            },
        )
    }

    fun showMessage(message: Int) {
        _messages.trySend(message)
    }

    private val _selectedTile = MutableStateFlow<TileCoord?>(null)
    val selectedTile: StateFlow<TileCoord?> = _selectedTile.asStateFlow()

    /** The object standing on the selected tile, if any. */
    val selectedObject: StateFlow<GridObject?> = combine(gridObjects, _selectedTile) { objects, tile ->
        objects.objectAt(tile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The player's slots in order, limited to the capacity they have unlocked. Kept up to date
     * all along, so that the bag opens with its contents already there.
     */
    val inventorySlots: StateFlow<List<InventorySlot>> =
        combine(gardenRepository.observeInventory(), gardenRepository.observeUserProfile()) { slots, profile ->
            if (profile == null) slots else slots.filter { it.slotIndex < profile.inventoryCapacity }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** How the bag is sorted; kept in the settings, so it stays as the player left it. */
    val inventorySort: StateFlow<InventorySort> = settingsRepository.settings.map { it.inventorySort }
        .stateIn(viewModelScope, SharingStarted.Eagerly, InventorySort.DEFAULT)

    fun setInventorySort(sort: InventorySort) {
        if (sort == inventorySort.value) return
        viewModelScope.launch { settingsRepository.update { it.copy(inventorySort = sort) } }
    }

    private val _placingSlotIndex = MutableStateFlow<Int?>(null)

    /** Non-null while in placement mode: the inventory slot whose item the next tile tap places. */
    val placingSlot: StateFlow<InventorySlot?> =
        combine(gardenRepository.observeInventory(), _placingSlotIndex) { slots, index ->
            slots.firstOrNull { it.slotIndex == index && !it.isEmpty }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _hoeActive = MutableStateFlow(false)

    /** True while the hoe is the tool in hand: a tap on a tile then tills it. */
    val hoeActive: StateFlow<Boolean> = _hoeActive.asStateFlow()

    private val _movingId = MutableStateFlow<String?>(null)

    /** Non-null in move mode: the object that the next tap on a tile moves there. */
    val movingObject: StateFlow<GridObject?> = combine(gridObjects, _movingId) { objects, id ->
        objects.firstOrNull { it.instanceId == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Empty until the atlas has loaded, and for good when no artwork is bundled. */
    private val _atlas = MutableStateFlow(SpriteAtlas.EMPTY)
    val atlas: StateFlow<SpriteAtlas> = _atlas.asStateFlow()

    private val _terrain = MutableStateFlow(TerrainTextures.EMPTY)
    val terrain: StateFlow<TerrainTextures> = _terrain.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)

    /** String resource ids of one-off messages (failed actions) to show the player. */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    private val _effects = MutableStateFlow<List<GardenEffect>>(emptyList())

    /** Floating feedback currently playing (rewards, level-ups). */
    val effects: StateFlow<List<GardenEffect>> = _effects.asStateFlow()
    private var nextEffectId = 0L

    // Sync and player actions all read-modify-write the same rows; run them one at a time.
    private val actionMutex = Mutex()
    private val _initialized = MutableStateFlow(false)
    private val initialized: StateFlow<Boolean> = _initialized.asStateFlow()
    private val initJob = viewModelScope.launch {
        gardenRepository.ensureInitialized()
        hoe.tillUnderExistingPlants(System.currentTimeMillis())
        followTutorial()
        _initialized.value = true
    }

    init {
        viewModelScope.launch { _atlas.value = assetRepository.loadAtlas() }
        viewModelScope.launch { _terrain.value = assetRepository.loadTerrainTextures() }
        audio.preload()
        watchForLevelUps()
        watchCollection()
        watchWork()
        watchVisitors()
        watchSeasonForMusic()
    }

    /** The garden's season as last read, for its music; null until the garden has been read. */
    private var musicSeason: Season? = null

    /** Whether the garden is on screen, so its music should be heard. */
    private var inForeground = false

    /**
     * Each season has its own tune. When the season turns while the player is here, the music
     * crossfades to the new one ([AudioManager.playBgm]); while away, the right tune is waiting
     * for [onForeground].
     */
    private fun watchSeasonForMusic() {
        viewModelScope.launch {
            gardenRepository.observeProgress().map { it?.season }.filterNotNull().distinctUntilChanged().collect { season ->
                musicSeason = season
                if (inForeground) audio.playBgm(BgmTrack.forSeason(season))
            }
        }
    }

    private val _levelUp = MutableStateFlow<LevelUpNotice?>(null)

    /** A level-up waiting on its board until the player taps OK; null when there is none. */
    val levelUp: StateFlow<LevelUpNotice?> = _levelUp.asStateFlow()

    private val _visitors = MutableStateFlow<List<CreatureVisit>>(emptyList())

    /** Creatures staying in the garden this period, see [CreatureVisits]. */
    val visitors: StateFlow<List<CreatureVisit>> = _visitors.asStateFlow()

    /** Visits already written into the nature notes, as (period, rule): each is written once. */
    private val notedVisits = HashSet<Pair<Long, VisitRule>>()

    /**
     * Works out who is visiting, at the start of every period and whenever the garden, its size
     * or the weather changes, and writes each visitor into the nature notes as it comes into view.
     */
    private fun watchVisitors() {
        val periods = flow {
            while (true) {
                val now = System.currentTimeMillis()
                val period = CreatureVisits.periodOf(now)
                emit(period)
                delay(CreatureVisits.periodStart(period + 1) - now + 1)
            }
        }
        viewModelScope.launch {
            initJob.join()
            combine(gridObjects, _weather, gardenMap, season, periods) { objects, weather, map, season, period ->
                Visiting(objects, weather, map, season, period)
            }
                .collectLatest { visiting ->
                    val now = System.currentTimeMillis()
                    val visits = CreatureVisits.visitorsAt(now, visiting.objects, visiting.weather, visiting.map, visiting.season)
                        .filter { it.leavesAt > now }
                    _visitors.value = visits
                    coroutineScope {
                        for (visit in visits) {
                            launch {
                                delay((visit.arrivesAt - now).coerceAtLeast(0L))
                                noteVisit(visiting.period, visit)
                            }
                        }
                    }
                }
        }
    }

    private class Visiting(val objects: List<GridObject>, val weather: WeatherType, val map: GardenMap, val season: Season, val period: Long)

    private suspend fun noteVisit(period: Long, visit: CreatureVisit) {
        if (!notedVisits.add(period to visit.rule)) return
        when (recordNatureNote(visit.rule, System.currentTimeMillis())) {
            NoteResult.NEW_CREATURE -> _messages.trySend(creatureMetMessage(visit.creature))
            NoteResult.NEW_COMBINATION -> _messages.trySend(R.string.msg_nature_note_added)
            NoteResult.SEEN_BEFORE -> Unit
        }
    }

    /**
     * The garden came to the foreground: start (or continue) the music of its season. Until the
     * garden has been read, whatever was playing (the title's tune) carries on, and gives way
     * to the season's as soon as it is known, rather than a guess starting first.
     */
    fun onForeground() {
        inForeground = true
        musicSeason?.let { audio.playBgm(BgmTrack.forSeason(it)) }
        audio.resumeBgm()
        // The player is here and sees the garden: nothing needs telling while they are.
        scheduleNotifications.cancel()
    }

    /**
     * The garden is leaving the foreground, and the process may be killed at any point after
     * this. Every action is already stored as it happens; this brings the plants and the
     * profile's clocks up to this very moment as well, so time away is measured from here.
     */
    fun onBackground() {
        inForeground = false
        audio.pauseBgm()
        sync()
        // What the garden will have to tell them while they are away. Worked out from what is
        // stored, which the sync above is bringing up to now; the plan runs the plants forward
        // from there either way.
        viewModelScope.launch {
            initJob.join()
            val weather: WeatherForecast = debugWeather ?: WeatherForecast.Daily
            withContext(Dispatchers.Default) { scheduleNotifications(System.currentTimeMillis(), weather) }
        }
    }

    /**
     * True when the one-time invitation to turn notifications on is due: the first steps are
     * over, the player has not switched notifications off, and they have not been asked before.
     * Whether the device needs asking at all is for the screen to tell.
     */
    val notificationInviteDue: StateFlow<Boolean> = combine(settingsRepository.settings, tutorialStep, initialized) { settings, step, ready ->
        // Only once the garden is known to be past its first steps: a new garden's progress is
        // still missing for a moment while it is being made, and that is not "done".
        ready && step == TutorialStep.DONE && settings.notificationsEnabled && !settings.notificationInviteShown
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** How the player last left the garden zoomed and panned, to open it the same way. */
    val savedView: StateFlow<GardenView?> = settingsRepository.settings.map { it.gardenView }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun saveView(view: GardenView) {
        if (view == savedView.value) return
        viewModelScope.launch { settingsRepository.update { it.copy(gardenView = view) } }
    }

    /**
     * The player has read the closing board of the first steps, or the introduction of a feature
     * that was just handed over: the next one comes, or the garden is theirs.
     */
    fun acknowledgeTutorial(seen: TutorialStep) {
        // The selection's panel would stand in front of what is being introduced.
        _selectedTile.value = null
        viewModelScope.launch {
            initJob.join()
            val now = actionMutex.withLock { followTutorial.acknowledge(seen) }
            now.introducing?.let { audio.play(SoundEffect.LEVEL_UP) }
        }
    }

    fun markNotificationInviteShown() {
        viewModelScope.launch { settingsRepository.update { it.copy(notificationInviteShown = true) } }
    }

    override fun onCleared() {
        audio.stopBgm()
    }

    /** Advances every plant to the current time. Called on resume and on a timer while visible. */
    fun sync() {
        viewModelScope.launch {
            initJob.join()
            var arrived: Season? = null
            val finished = actionMutex.withLock {
                val now = System.currentTimeMillis()
                // Jobs are only finished on time that can be trusted; otherwise the sync has
                // just restarted them.
                (if (syncGarden(now, currentWeather(now)).isTrusted) completeWork(now) else emptyList())
                    // A plant harvested or cleared away under the first steps lets them go.
                    .also { followTutorial() }
                    // The year turns, and the post brings word of it.
                    .also { arrived = advanceSeason(now) }
                    // Now and then, a word of advice.
                    .also { postLetters.tipIfDue(now) }
            }
            arrived?.let(::announceSeason)
            if (finished.isNotEmpty()) {
                audio.play(if (finished.any { it.kind == WorkKind.READY }) SoundEffect.COIN else SoundEffect.PLANT)
                // A finished building is not announced yet, only pointed out: it is the player's
                // tap that unveils it. What has been taken apart is done and is announced.
                if (finished.any { it.kind == WorkKind.READY }) _messages.trySend(R.string.msg_work_ready)
                finished.filter { it.kind != WorkKind.READY }
                    .forEach { done -> showEffect { GardenEffect.WorkDone(it, done.objectId, done.kind) } }
            }
        }
    }

    /**
     * Wakes up when the next building or demolition job is due and syncs, which finishes it.
     * Nothing is polled: whenever the garden changes, the wait is set again for the job that
     * will be done soonest, and with no jobs there is nothing to wait for.
     */
    private fun watchWork() {
        viewModelScope.launch {
            gridObjects.collectLatest { objects ->
                val nextDue = objects.filter { it.isWorking && it.workKind != WorkKind.READY }
                    .minOfOrNull { it.workEndsAt } ?: return@collectLatest
                delay((nextDue - System.currentTimeMillis()).coerceAtLeast(0L) + WORK_GRACE_MILLIS)
                sync()
            }
        }
    }

    /**
     * With the hoe in hand a tap on a tile tills it, and in placement mode it places the chosen
     * item there. Otherwise it selects the tile; tapping the selected tile again, or anywhere
     * off the grid, clears the selection.
     */
    fun onTileTapped(tile: TileCoord?) {
        val slotIndex = _placingSlotIndex.value
        val movingId = _movingId.value
        if (movingId != null) {
            if (tile != null) {
                act(
                    onSuccess = {
                        _movingId.value = null
                        _selectedTile.value = tile
                        audio.play(SoundEffect.PLANT)
                    }
                ) { _, _ -> moveObject(movingId, tile.x, tile.y) }
            }
        } else if (slotIndex == null && !_hoeActive.value && gridObjects.value.objectAt(tile)?.isStarterCrate == true) {
            // The starter crate is not selected like other things: a tap opens it.
            _selectedTile.value = null
            _openingCrateId.value = gridObjects.value.objectAt(tile)?.instanceId
        } else if (slotIndex == null && !_hoeActive.value && gridObjects.value.objectAt(tile)?.workKind == WorkKind.READY) {
            // A finished building under wraps: the tap unveils it.
            val site = gridObjects.value.objectAt(tile) ?: return
            act(
                onSuccess = {
                    _selectedTile.value = null
                    audio.play(SoundEffect.LEVEL_UP)
                    showEffect { GardenEffect.WorkDone(it, site.objectId, WorkKind.BUILDING) }
                }
            ) { _, _ -> unveilBuilding(site.instanceId) }
        } else if (_hoeActive.value) {
            if (tile != null) {
                act(onSuccess = { audio.play(SoundEffect.PLANT) }) { now, _ ->
                    hoe(tile.x, tile.y, now).also { if (it == ActionResult.SUCCESS) followTutorial() }
                }
            }
        } else if (slotIndex == null) {
            _selectedTile.value = tile.takeIf { it != _selectedTile.value }
        } else if (tile != null) {
            val item = placingSlot.value?.let { GardenItem.fromItemId(it.itemId) }
            val placeXp = item?.placeXp ?: 0
            act(
                onSuccess = {
                    audio.play(SoundEffect.PLANT)
                    if (placeXp > 0) showEffect { GardenEffect.Xp(it, placeXp) }
                    if ((item?.buildSeconds ?: 0) > 0) _messages.trySend(R.string.work_started_building)
                }
            ) { now, _ ->
                placeItem(slotIndex, tile.x, tile.y, now).also {
                    val slotNowEmpty = gardenRepository.getInventory()
                        .none { it.slotIndex == slotIndex && !it.isEmpty }
                    if (slotNowEmpty) _placingSlotIndex.value = null
                    if (it == ActionResult.SUCCESS) followTutorial()
                }
            }
        }
    }

    /** Picks the hoe up, or puts it down again. Picking it up ends any placing and selection. */
    fun toggleHoe() {
        val active = !_hoeActive.value
        if (active) {
            _selectedTile.value = null
            _placingSlotIndex.value = null
            _movingId.value = null
        }
        _hoeActive.value = active
    }

    /** Enters move mode for the selected object: the next tap on a tile moves it there. */
    fun startMovingSelected() = withSelectedObject { obj ->
        when {
            !MoveObjectUseCase.canMove(obj) -> _messages.trySend(R.string.msg_not_movable)
            obj.isWorking -> _messages.trySend(R.string.msg_busy)
            else -> {
                _hoeActive.value = false
                _placingSlotIndex.value = null
                _selectedTile.value = null
                _movingId.value = obj.instanceId
            }
        }
    }

    fun cancelMoving() {
        _movingId.value = null
    }

    /** Puts down whatever is in hand or selected, ready for a photograph of the garden alone. */
    fun enterCameraMode() {
        _selectedTile.value = null
        _placingSlotIndex.value = null
        _movingId.value = null
        _hoeActive.value = false
    }

    fun startPlacing(slotIndex: Int) {
        _hoeActive.value = false
        _movingId.value = null
        _selectedTile.value = null
        _placingSlotIndex.value = slotIndex
    }

    fun cancelPlacing() {
        _placingSlotIndex.value = null
    }

    fun clearSelection() {
        _selectedTile.value = null
    }

    fun waterSelected() = withSelectedObject { obj ->
        act(
            onSuccess = {
                audio.play(SoundEffect.WATER)
                showEffect { GardenEffect.Xp(it, CareForPlantUseCase.WATER_XP) }
            },
        ) { now, weather ->
            careForPlant.water(obj.instanceId, now, weather).also {
                // Under the first steps a try counts even when the soil is moist already (rain).
                // What follows is the closing board, shown by the screen from the stored step.
                if (it == ActionResult.SUCCESS || it == ActionResult.ALREADY_MOIST) followTutorial.wateringDone()
            }
        }
    }

    /** Opens the starter crate whose popup is showing, and puts its seed in the bag. */
    fun openCrate() {
        val crateId = _openingCrateId.value ?: return
        act(
            onSuccess = {
                _openingCrateId.value = null
                audio.play(SoundEffect.COIN)
            }
        ) { _, _ ->
            openStarterCrate(crateId).also { if (it == ActionResult.SUCCESS) followTutorial() }
        }
    }

    fun closeCrate() {
        _openingCrateId.value = null
    }

    /**
     * Grows the garden by a ring of lawn. Everything in it moves one cell along, so the selection
     * (which is a cell, not a thing) is let go of.
     */
    fun expand() {
        act(
            onSuccess = {
                _selectedTile.value = null
                audio.play(SoundEffect.LEVEL_UP)
                _messages.trySend(R.string.msg_garden_expanded)
            }
        ) { _, _ -> expandGarden() }
    }

    fun fertilizeSelected() = withSelectedObject { obj ->
        act(
            onSuccess = {
                audio.play(SoundEffect.FERTILIZE)
                showEffect { GardenEffect.Xp(it, CareForPlantUseCase.FERTILIZE_XP) }
            }
        ) { now, weather -> careForPlant.fertilize(obj.instanceId, now, weather) }
    }

    fun harvestSelected() = withSelectedObject { obj ->
        val species = PlantSpecies.forObjectId(obj.objectId)
        act(
            onSuccess = {
                clearSelection()
                audio.play(SoundEffect.HARVEST)
                showEffect { GardenEffect.Harvest(it, species.harvestCoins, species.harvestXp) }
            }
        ) { now, weather -> harvestPlant(obj.instanceId, now, weather) }
    }

    fun removeSelected() = withSelectedObject { obj ->
        // A withered plant standing on tilled soil is dug in as compost; say so.
        val composts = RemoveObjectUseCase.leavesCompost(obj) &&
            GardenGrid(gardenMap.value, gridObjects.value).isTilled(obj.gridX, obj.gridY)
        val takesTime = takesTimeToRemove(obj)
        act(
            onSuccess = {
                clearSelection()
                if (composts) _messages.trySend(R.string.msg_composted)
                if (takesTime) _messages.trySend(R.string.work_started_demolition)
            }
        ) { now, _ -> startRemoval(obj.instanceId, toBag = false, now) }
    }

    /** Whether removing [obj] starts a job instead of happening at once. */
    private fun takesTimeToRemove(obj: GridObject): Boolean =
        !obj.isWorking && (StoreObjectUseCase.itemFor(obj)?.demolishSeconds ?: 0) > 0

    /** Takes the selected object off the garden and puts it back into the inventory. */
    fun storeSelected() = withSelectedObject { obj ->
        val takesTime = takesTimeToRemove(obj)
        act(
            onSuccess = {
                clearSelection()
                audio.play(SoundEffect.PLANT)
                _messages.trySend(if (takesTime) R.string.work_started_demolition else R.string.msg_stored)
            }
        ) { now, _ -> startRemoval(obj.instanceId, toBag = true, now) }
    }

    /** Pays to bring the selected withered plant back. */
    fun reviveSelected() = withSelectedObject { obj ->
        act(
            onSuccess = {
                audio.play(SoundEffect.WATER)
                _messages.trySend(R.string.msg_revived)
            }
        ) { now, weather -> careForPlant.revive(obj.instanceId, now, weather) }
    }

    /** Debug menu: pretend [millis] passed for everything in the garden. */
    fun debugSkipTime(millis: Long) = act(onSuccess = ::sync) { now, weather -> skipTime(millis, now, weather) }

    /** Debug menu: 500 more coins and the experience for the next level. */
    fun debugBoost() {
        viewModelScope.launch {
            initJob.join()
            actionMutex.withLock {
                gardenRepository.runInTransaction {
                    val profile = gardenRepository.getUserProfile() ?: return@runInTransaction
                    val toNext = (LevelTable.xpForLevel(profile.playerLevel + 1) - profile.currentXp).coerceAtLeast(0L)
                    gardenRepository.saveUserProfile(profile.copy(coins = profile.coins + 500).gainXp(toNext.toInt()))
                }
            }
        }
    }

    /** Debug menu: switch to the next kind of weather, in place of the day's own, until restart. */
    fun debugCycleWeather() {
        val next = WeatherType.entries[(weather.value.ordinal + 1) % WeatherType.entries.size]
        debugWeather = next
        _weather.value = next
    }

    fun onEffectFinished(id: Long) {
        _effects.update { list -> list.filterNot { it.id == id } }
    }

    private fun showEffect(create: (id: Long) -> GardenEffect) {
        _effects.update { it + create(nextEffectId++) }
    }

    /**
     * Raises the level-up board whenever the stored level rises above the one last seen. Levels
     * gained while the board is still up join it ([LevelUpNotice.after]).
     */
    private fun watchForLevelUps() {
        viewModelScope.launch {
            var lastLevel: Int? = null
            gardenRepository.observeUserProfile().collect { profile ->
                val level = profile?.playerLevel ?: return@collect
                lastLevel?.let { previous ->
                    if (level > previous) {
                        audio.play(SoundEffect.LEVEL_UP)
                        _levelUp.update { LevelUpNotice.after(it, previous, level) }
                        // The shop's news also comes by post, to be read again later.
                        postLetters.newStock(previous, level, System.currentTimeMillis())
                    }
                }
                lastLevel = level
            }
        }
    }

    /** The player tapped OK on the level-up board. */
    fun acknowledgeLevelUp() {
        _levelUp.value = null
    }

    /**
     * Says so when a page of the encyclopedia becomes ready. Filling it in, and the coins for
     * that, happen in the book, at the player's tap.
     */
    private fun watchCollection() {
        viewModelScope.launch {
            var known: Set<String>? = null
            gardenRepository.observeCollection().collect { records ->
                val now = records.mapTo(HashSet()) { it.entryId }
                val before = known
                known = now
                // The first emission is what was already there, not news.
                if (before == null) return@collect

                if (CollectionEntry.entries.any { it.objectId in now && it.objectId !in before }) {
                    showEffect { GardenEffect.Discovery(it) }
                }
            }
        }
    }

    private fun withSelectedObject(block: (GridObject) -> Unit) {
        val obj = gridObjects.value.objectAt(_selectedTile.value)
        if (obj == null) _messages.trySend(R.string.msg_select_plant) else block(obj)
    }

    private fun act(
        onSuccess: () -> Unit = {},
        block: suspend (now: Long, weather: WeatherForecast) -> ActionResult,
    ) {
        viewModelScope.launch {
            initJob.join()
            val result = actionMutex.withLock {
                val now = System.currentTimeMillis()
                block(now, currentWeather(now))
            }
            if (result == ActionResult.SUCCESS) onSuccess() else audio.play(SoundEffect.ERROR)
            actionResultMessage(result)?.let { _messages.send(it) }
        }
    }

    /**
     * The weather to bring plants up to [now] with: day by day as it really was, so that time
     * away is weighed correctly, or the debug menu's choice throughout. Also refreshes the
     * weather shown on screen.
     */
    private fun currentWeather(now: Long): WeatherForecast {
        val forecast: WeatherForecast = debugWeather ?: WeatherForecast.Daily
        _weather.value = forecast.at(now)
        return forecast
    }

    /** The object covering [tile]; for a structure that is any cell of its footprint. */
    private fun List<GridObject>.objectAt(tile: TileCoord?): GridObject? =
        if (tile == null) null else GardenGrid(gardenMap.value, this).objectAt(tile.x, tile.y)

    private companion object {
        const val BLESSED_RAIN_SHOWER_MILLIS = 4_000L

        /** Waited past a job's end before syncing, so that the sync is sure to find it due. */
        const val WORK_GRACE_MILLIS = 60L
    }
}
