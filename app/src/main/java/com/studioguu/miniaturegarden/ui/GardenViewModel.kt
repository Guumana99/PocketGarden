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
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.TutorialTool
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
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
import com.studioguu.miniaturegarden.domain.usecase.GardenLayout
import com.studioguu.miniaturegarden.domain.usecase.LayoutAction
import com.studioguu.miniaturegarden.domain.usecase.LayoutCheck
import com.studioguu.miniaturegarden.domain.usecase.AdvanceSeasonUseCase
import com.studioguu.miniaturegarden.domain.usecase.PostLettersUseCase
import com.studioguu.miniaturegarden.domain.usecase.ReadLettersUseCase
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase
import com.studioguu.miniaturegarden.domain.usecase.CompleteWorkUseCase
import com.studioguu.miniaturegarden.domain.usecase.ExpandGardenUseCase
import com.studioguu.miniaturegarden.domain.usecase.NoteResult
import com.studioguu.miniaturegarden.domain.usecase.RecordNatureNoteUseCase
import com.studioguu.miniaturegarden.domain.usecase.ScheduleGardenNotificationsUseCase
import com.studioguu.miniaturegarden.domain.usecase.FlipObjectUseCase
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
import com.studioguu.miniaturegarden.ui.effects.plusEffect
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
    private val flipObject: FlipObjectUseCase,
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

    /** What stands in the garden as stored; null until it has been read. */
    private val storedObjects: StateFlow<List<GridObject>?> = gardenRepository.observeGridObjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val gridObjects: StateFlow<List<GridObject>> = storedObjects.map { it.orEmpty() }
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

    private val _placingFlipped = MutableStateFlow(false)

    /** Which way a building being placed will face; switched on the placing banner. */
    val placingFlipped: StateFlow<Boolean> = _placingFlipped.asStateFlow()

    /** Turns the building in hand to face the other way before it is put down. */
    fun togglePlacingFlip() {
        _placingFlipped.value = !_placingFlipped.value
    }

    /** Non-null while in placement mode: the inventory slot whose item the next tile tap places. */
    val placingSlot: StateFlow<InventorySlot?> =
        combine(gardenRepository.observeInventory(), _placingSlotIndex) { slots, index ->
            slots.firstOrNull { it.slotIndex == index && !it.isEmpty }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editMode = MutableStateFlow(false)

    /**
     * True in edit mode: the grid shows, and the garden is rearranged: things are moved, put back
     * or cleared away. Nothing is grown, tended or newly put down there (#50): the hoe, watering,
     * feeding, harvesting and the bag are put away. See [GardenLayout].
     */
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    /**
     * Turns edit mode on or off. Going in puts down the hoe and whatever was in hand from the bag
     * (those are the everyday garden's); coming out puts down what was on the move. The selection
     * stays as it is.
     */
    fun toggleEditMode() {
        val on = !_editMode.value
        _editMode.value = on
        if (on) {
            _hoeActive.value = false
            _placingSlotIndex.value = null
        } else {
            _movingId.value = null
        }
    }

    /** Says that edit mode keeps to rearranging, and whether it is on (so the caller stops). */
    private fun lockedByEditMode(): Boolean {
        if (_editMode.value) _messages.trySend(R.string.msg_edit_mode_tools)
        return _editMode.value
    }

    /** Tells the player why [check] stopped them, and whether it did. */
    private fun refused(check: LayoutCheck): Boolean {
        when (check) {
            LayoutCheck.OK -> return false
            LayoutCheck.NEEDS_EDIT_MODE -> _messages.trySend(R.string.msg_needs_edit_mode)
            LayoutCheck.IN_THE_GROUND, LayoutCheck.FIXED -> _messages.trySend(R.string.msg_not_movable)
        }
        return true
    }

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

    private val _artLoaded = MutableStateFlow(false)

    /**
     * Whether everything the garden is drawn from has been read: what stands in it, its size and
     * season, and its artwork. Until then the screen shows the sky alone, never the default 9 x 9
     * lawn or the stand-in shapes for a moment before the saved garden replaces them.
     */
    val gardenReady: StateFlow<Boolean> = combine(storedObjects, progress, gardenMap, _artLoaded) { objects, saved, map, art ->
        isGardenReady(objects, saved, map, art)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
        viewModelScope.launch {
            coroutineScope {
                launch { _atlas.value = assetRepository.loadAtlas() }
                launch { _terrain.value = assetRepository.loadTerrainTextures() }
            }
            _artLoaded.value = true
        }
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

    /**
     * The symbol building to point at while the garden can grow now ([GardenExpansion.growableFrom]):
     * a bubble over it says so, and tapping the bubble selects it, whose panel has the button.
     */
    val growableBuilding: StateFlow<GridObject?> =
        combine(gridObjects, gardenMap, profile) { objects, map, profile ->
            GardenExpansion.growableFrom(map.size, objects, profile?.playerLevel ?: 1)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Posts the letter saying the garden can grow, the first time it can grow to a size, and says
     * so once on screen as well; after that the bubble over the symbol building keeps pointing.
     */
    private suspend fun announceGrowthIfDue(now: Long) {
        if (postLetters.gardenCanGrowIfDue(now) != null) _messages.trySend(R.string.msg_garden_can_grow)
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
                    // And word when the garden can grow (in case it was missed when it became so).
                    .also { announceGrowthIfDue(now) }
            }
            arrived?.let(::announceSeason)
            if (finished.isNotEmpty()) {
                // A building done is good news, not a purchase: the celebration's sound (#50).
                audio.play(if (finished.any { it.kind == WorkKind.READY }) SoundEffect.LEVEL_UP else SoundEffect.PLANT)
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
                val footprint = gridObjects.value.firstOrNull { it.instanceId == movingId }?.footprint ?: Footprint.SINGLE
                val origin = footprint.originFor(tile.x, tile.y, gardenMap.value.size)
                act(
                    onSuccess = {
                        _movingId.value = null
                        _selectedTile.value = tile
                        audio.play(SoundEffect.PLANT)
                    }
                ) { _, _ -> moveObject(movingId, origin.x, origin.y) }
            }
        } else if (slotIndex == null && !_hoeActive.value && gridObjects.value.objectAt(tile)?.isStarterCrate == true) {
            // The starter crate is not selected like other things: a tap opens it, in the
            // everyday garden (no edit mode, no grid).
            _editMode.value = false
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
                    // Its experience comes now, not when the site went up (#50).
                    val xp = StoreObjectUseCase.itemFor(site)?.placeXp ?: 0
                    if (xp > 0) showEffect { GardenEffect.Xp(it, xp) }
                }
            ) { now, _ -> unveilBuilding(site.instanceId, now).also { if (it == ActionResult.SUCCESS) announceGrowthIfDue(now) } }
        } else if (_hoeActive.value) {
            if (tile != null) {
                act(
                    onSuccess = {
                        audio.play(SoundEffect.PLANT)
                        // Under the first steps one cell is enough: the hoe is put down, and
                        // sowing is next.
                        if (tutorialStep.value?.isGuiding == true) _hoeActive.value = false
                    }
                ) { now, _ ->
                    hoe(tile.x, tile.y, now).also { if (it == ActionResult.SUCCESS) followTutorial() }
                }
            }
        } else if (slotIndex == null) {
            // Tapping what is already selected puts it down again, whichever of its cells (or
            // its roof, see TapTargets) the tap landed on.
            val objects = gridObjects.value
            val current = _selectedTile.value
            val sameThing = objects.objectAt(tile)?.let { it.instanceId == objects.objectAt(current)?.instanceId } == true
            _selectedTile.value = tile.takeIf { it != current && !sameThing }
        } else if (tile != null) {
            val item = placingSlot.value?.let { GardenItem.fromItemId(it.itemId) }
            // Nothing new is put down in edit mode (#50); in case it was turned on since.
            if (_editMode.value) {
                _placingSlotIndex.value = null
                _messages.trySend(R.string.msg_edit_mode_tools)
                return
            }
            // A building earns its experience when it is unveiled, not now (#50).
            val placeXp = if ((item?.buildSeconds ?: 0) > 0) 0 else item?.placeXp ?: 0
            // A building larger than one cell is fitted round the tapped cell, inside the garden.
            val footprint = item?.let { Footprint.forObjectId(it.objectId) } ?: Footprint.SINGLE
            val origin = footprint.originFor(tile.x, tile.y, gardenMap.value.size)
            act(
                onSuccess = {
                    audio.play(SoundEffect.PLANT)
                    if (placeXp > 0) showEffect { GardenEffect.Xp(it, placeXp) }
                    if ((item?.buildSeconds ?: 0) > 0) _messages.trySend(R.string.work_started_building)
                }
            ) { now, _ ->
                placeItem(slotIndex, origin.x, origin.y, now, flipped = _placingFlipped.value).also {
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
        // Under the first steps the hoe is only handed out for the tilling step, and never in edit mode.
        if (active && (lockedByEditMode() || !allows(TutorialTool.HOE))) return
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
            refused(GardenLayout.check(LayoutAction.MOVE, obj, _editMode.value)) -> Unit
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
        _editMode.value = false
        _selectedTile.value = null
        _placingSlotIndex.value = null
        _movingId.value = null
        _hoeActive.value = false
    }

    /**
     * Whether [tool] can be picked up now: at the current first step ([TutorialStep.allows]), and
     * outside edit mode unless it is the shop ([GardenLayout.toolAllowedInEditMode]).
     */
    fun allows(tool: TutorialTool): Boolean =
        (tutorialStep.value?.allows(tool) ?: true) && (!_editMode.value || GardenLayout.toolAllowedInEditMode(tool))

    /**
     * The bag's button: opens the bag, or with something already in hand from it, puts that down
     * again (a tool is put down by tapping its button again, #50).
     *
     * @return true if the bag is to be opened.
     */
    fun onBagTapped(): Boolean {
        if (_placingSlotIndex.value != null) {
            cancelPlacing()
            return false
        }
        return !lockedByEditMode()
    }

    fun startPlacing(slotIndex: Int) {
        // Things are put down in the everyday garden, not in edit mode (#50).
        if (lockedByEditMode()) return
        // Each thing taken out of the bag starts facing the usual way.
        _placingFlipped.value = false
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
        if (lockedByEditMode()) return@withSelectedObject
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
                // A seed is received, not bought: a soft tap rather than the till's coins.
                audio.play(SoundEffect.TAP)
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
        if (lockedByEditMode()) return@withSelectedObject
        act(
            onSuccess = {
                audio.play(SoundEffect.FERTILIZE)
                showEffect { GardenEffect.Xp(it, CareForPlantUseCase.FERTILIZE_XP) }
            }
        ) { now, weather -> careForPlant.fertilize(obj.instanceId, now, weather) }
    }

    fun harvestSelected() = withSelectedObject { obj ->
        if (lockedByEditMode()) return@withSelectedObject
        // A crop that bears again pays less from its second harvest on.
        val reward = PlantSpecies.forObjectId(obj.objectId).harvestReward(obj.harvestCount)
        act(
            onSuccess = {
                clearSelection()
                audio.play(SoundEffect.HARVEST)
                showEffect { GardenEffect.Harvest(it, reward.coins, reward.xp) }
            }
        ) { now, weather -> harvestPlant(obj.instanceId, now, weather) }
    }

    fun removeSelected() = withSelectedObject { obj ->
        if (refused(GardenLayout.check(LayoutAction.DISCARD, obj, _editMode.value))) return@withSelectedObject
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
        if (refused(GardenLayout.check(LayoutAction.STORE, obj, _editMode.value))) return@withSelectedObject
        val takesTime = takesTimeToRemove(obj)
        act(
            onSuccess = {
                clearSelection()
                audio.play(SoundEffect.PLANT)
                _messages.trySend(if (takesTime) R.string.work_started_demolition else R.string.msg_stored)
            }
        ) { now, _ -> startRemoval(obj.instanceId, toBag = true, now) }
    }

    /** Turns the selected building to face the other way, where it stands. */
    fun flipSelected() = withSelectedObject { obj ->
        act(onSuccess = { audio.play(SoundEffect.PLANT) }) { _, _ -> flipObject(obj.instanceId) }
    }

    /** Pays to bring the selected withered plant back. */
    fun reviveSelected() = withSelectedObject { obj ->
        if (lockedByEditMode()) return@withSelectedObject
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
        _effects.update { it.plusEffect(create(nextEffectId++)) }
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
                        // The level for the garden's next size may have come with it.
                        actionMutex.withLock { announceGrowthIfDue(System.currentTimeMillis()) }
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

/**
 * The garden can be shown once what stands in it ([objects]) and its progress ([saved]) have been
 * read, the lawn drawn ([map]) is already the saved garden's size, and the artwork has loaded
 * ([artLoaded]). Anything less would show the default garden, or stand-in shapes, for a moment.
 */
internal fun isGardenReady(objects: List<GridObject>?, saved: GardenProgress?, map: GardenMap, artLoaded: Boolean): Boolean =
    objects != null && saved != null && map.size == saved.gardenSize && artLoaded
