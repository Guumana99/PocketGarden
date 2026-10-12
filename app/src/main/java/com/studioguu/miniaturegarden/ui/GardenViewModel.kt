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
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.RewardType
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.repository.RewardLimitRepository
import com.studioguu.miniaturegarden.domain.usecase.ActionResult
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase
import com.studioguu.miniaturegarden.domain.usecase.GrantRewardUseCase
import com.studioguu.miniaturegarden.domain.usecase.HarvestPlantUseCase
import com.studioguu.miniaturegarden.domain.usecase.HoeUseCase
import com.studioguu.miniaturegarden.domain.usecase.RewardResult
import com.studioguu.miniaturegarden.domain.usecase.PlaceItemUseCase
import com.studioguu.miniaturegarden.domain.usecase.RemoveObjectUseCase
import com.studioguu.miniaturegarden.domain.usecase.SkipTimeUseCase
import com.studioguu.miniaturegarden.domain.usecase.SwitchFencePartUseCase
import com.studioguu.miniaturegarden.domain.usecase.SyncGardenUseCase
import com.studioguu.miniaturegarden.ui.components.todayEpochDay
import com.studioguu.miniaturegarden.ui.effects.GardenEffect
import com.studioguu.miniaturegarden.ui.render.TileCoord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val removeObject: RemoveObjectUseCase,
    private val skipTime: SkipTimeUseCase,
    private val switchFencePart: SwitchFencePartUseCase,
    private val hoe: HoeUseCase,
    private val audio: AudioManager,
    val gardenMap: GardenMap,
    private val grantReward: GrantRewardUseCase,
    private val rewardedAds: RewardedAdManager,
    rewardLimits: RewardLimitRepository,
) : ViewModel() {

    val gridObjects: StateFlow<List<GridObject>> = gardenRepository.observeGridObjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profile: StateFlow<UserProfile?> = gardenRepository.observeUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _weather = MutableStateFlow(WeatherType.forTime(System.currentTimeMillis()))
    val weather: StateFlow<WeatherType> = _weather.asStateFlow()

    private var debugWeather: WeatherType? = null

    /** Blessed rains still available today. */
    val rainRemaining: StateFlow<Int> = rewardLimits.usage
        .map { it.remaining(RewardType.BLESSED_RAIN, todayEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val adReady: StateFlow<Boolean> = rewardedAds.isReady

    /** Pages of the encyclopedia waiting for the player to fill them in. */
    val collectionReady: StateFlow<Int> = gardenRepository.observeCollection()
        .map { CollectionProgress(it).ready }
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

    private val _placingSlotIndex = MutableStateFlow<Int?>(null)

    /** Non-null while in placement mode: the inventory slot whose item the next tile tap places. */
    val placingSlot: StateFlow<InventorySlot?> =
        combine(gardenRepository.observeInventory(), _placingSlotIndex) { slots, index ->
            slots.firstOrNull { it.slotIndex == index && !it.isEmpty }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _hoeActive = MutableStateFlow(false)

    /** True while the hoe is the tool in hand: a tap on a tile then tills it. */
    val hoeActive: StateFlow<Boolean> = _hoeActive.asStateFlow()

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
    private val initJob = viewModelScope.launch {
        gardenRepository.ensureInitialized()
        hoe.tillUnderExistingPlants(System.currentTimeMillis())
    }

    init {
        viewModelScope.launch { _atlas.value = assetRepository.loadAtlas() }
        viewModelScope.launch { _terrain.value = assetRepository.loadTerrainTextures() }
        audio.preload()
        watchForLevelUps()
        watchCollection()
    }

    /** The garden came to the foreground: start (or continue) its music. */
    fun onForeground() {
        audio.playBgm(BgmTrack.GARDEN)
        audio.resumeBgm()
    }

    /**
     * The garden is leaving the foreground, and the process may be killed at any point after
     * this. Every action is already stored as it happens; this brings the plants and the
     * profile's clocks up to this very moment as well, so time away is measured from here.
     */
    fun onBackground() {
        audio.pauseBgm()
        sync()
    }

    override fun onCleared() {
        audio.stopBgm()
    }

    /** Advances every plant to the current time. Called on resume and on a timer while visible. */
    fun sync() {
        viewModelScope.launch {
            initJob.join()
            actionMutex.withLock {
                val now = System.currentTimeMillis()
                syncGarden(now, currentWeather(now))
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
        if (_hoeActive.value) {
            if (tile != null) {
                act(onSuccess = { audio.play(SoundEffect.PLANT) }) { now, _ -> hoe(tile.x, tile.y, now) }
            }
        } else if (slotIndex == null) {
            _selectedTile.value = tile.takeIf { it != _selectedTile.value }
        } else if (tile != null) {
            val placeXp = placingSlot.value?.let { GardenItem.fromItemId(it.itemId) }?.placeXp ?: 0
            act(
                onSuccess = {
                    audio.play(SoundEffect.PLANT)
                    if (placeXp > 0) showEffect { GardenEffect.Xp(it, placeXp) }
                }
            ) { now, _ ->
                placeItem(slotIndex, tile.x, tile.y, now).also {
                    val slotNowEmpty = gardenRepository.getInventory()
                        .none { it.slotIndex == slotIndex && !it.isEmpty }
                    if (slotNowEmpty) _placingSlotIndex.value = null
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
        }
        _hoeActive.value = active
    }

    fun startPlacing(slotIndex: Int) {
        _hoeActive.value = false
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
            }
        ) { now, weather -> careForPlant.water(obj.instanceId, now, weather) }
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
        act(onSuccess = ::clearSelection) { _, _ -> removeObject(obj.instanceId) }
    }

    /** Shows the selected fence as the next of its parts. */
    fun switchSelectedFencePart() = withSelectedObject { obj ->
        act(onSuccess = { audio.play(SoundEffect.PLANT) }) { _, _ -> switchFencePart(obj.instanceId) }
    }

    /** Debug menu: pretend [millis] passed for everything in the garden. */
    fun debugSkipTime(millis: Long) = act { now, weather -> skipTime(millis, now, weather) }

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
     * Announces a level-up whenever the stored level rises above the one last seen, and says so
     * when the new level opens something in the shop.
     */
    private fun watchForLevelUps() {
        viewModelScope.launch {
            var lastLevel: Int? = null
            gardenRepository.observeUserProfile().collect { profile ->
                val level = profile?.playerLevel ?: return@collect
                lastLevel?.let { previous ->
                    if (level > previous) {
                        audio.play(SoundEffect.LEVEL_UP)
                        showEffect { GardenEffect.LevelUp(it, level) }
                        if (GardenItem.unlockedBetween(previous, level).isNotEmpty()) {
                            _messages.trySend(R.string.msg_new_items_unlocked)
                        }
                    }
                }
                lastLevel = level
            }
        }
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
        block: suspend (now: Long, weather: WeatherType) -> ActionResult,
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

    private fun currentWeather(now: Long): WeatherType =
        (debugWeather ?: WeatherType.forTime(now)).also { _weather.value = it }

    /** The object covering [tile]; for a structure that is any cell of its footprint. */
    private fun List<GridObject>.objectAt(tile: TileCoord?): GridObject? =
        if (tile == null) null else GardenGrid(gardenMap, this).objectAt(tile.x, tile.y)

    private companion object {
        const val BLESSED_RAIN_SHOWER_MILLIS = 4_000L
    }
}
