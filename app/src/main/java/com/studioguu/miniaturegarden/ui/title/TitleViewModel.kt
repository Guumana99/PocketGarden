package com.studioguu.miniaturegarden.ui.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studioguu.miniaturegarden.data.asset.AssetRepository
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TerrainTextures
import com.studioguu.miniaturegarden.data.audio.AudioManager
import com.studioguu.miniaturegarden.data.audio.BgmTrack
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TitleViewModel @Inject constructor(
    private val gardenRepository: GardenRepository,
    assetRepository: AssetRepository,
    private val audio: AudioManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _hasSavedGarden = MutableStateFlow<Boolean?>(null)

    /** Whether there is a garden to continue; null until that has been looked up. */
    val hasSavedGarden: StateFlow<Boolean?> = _hasSavedGarden.asStateFlow()

    private val _atlas = MutableStateFlow(SpriteAtlas.EMPTY)
    val atlas: StateFlow<SpriteAtlas> = _atlas.asStateFlow()

    private val _terrain = MutableStateFlow(TerrainTextures.EMPTY)
    val terrain: StateFlow<TerrainTextures> = _terrain.asStateFlow()

    private val _artLoaded = MutableStateFlow(false)

    /** Whether the artwork has been read: the sample garden is shown from then on, never in stand-in shapes. */
    val artLoaded: StateFlow<Boolean> = _artLoaded.asStateFlow()

    private var busy = false

    init {
        viewModelScope.launch { _hasSavedGarden.value = gardenRepository.hasSavedGarden() }
        // The same artwork the garden uses; it stays loaded for the garden afterwards.
        viewModelScope.launch {
            coroutineScope {
                launch { _atlas.value = assetRepository.loadAtlas() }
                launch { _terrain.value = assetRepository.loadTerrainTextures() }
            }
            _artLoaded.value = true
        }
    }

    /**
     * The title is in front: its music plays. Until the title has a tune of its own it plays the
     * spring one ([BgmTrack.FALLBACK_RESOURCE]); then, opening a spring garden, the music simply
     * carries on, and opening one in another season crossfades to that season's tune.
     */
    fun onForeground() {
        audio.playBgm(BgmTrack.TITLE)
        audio.resumeBgm()
    }

    /** The app has left the foreground from the title. */
    fun onBackground() {
        audio.pauseBgm()
    }

    /**
     * Throws the saved garden away, then calls [onDone]: the garden screen sets up a new one
     * as it opens. The view kept from the last garden (its zoom and pan) goes with it, so a new
     * garden always opens at the starting zoom ([com.studioguu.miniaturegarden.ui.render.GardenCamera.starting]).
     * Taps while this is under way are ignored.
     */
    fun startOver(onDone: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            gardenRepository.deleteSavedGarden()
            settingsRepository.update { it.copy(gardenView = null) }
            _hasSavedGarden.value = false
            onDone()
        }
    }
}
