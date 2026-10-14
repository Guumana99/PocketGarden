package com.studioguu.miniaturegarden.ui.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studioguu.miniaturegarden.data.asset.AssetRepository
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TerrainTextures
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TitleViewModel @Inject constructor(
    private val gardenRepository: GardenRepository,
    assetRepository: AssetRepository,
) : ViewModel() {

    private val _hasSavedGarden = MutableStateFlow<Boolean?>(null)

    /** Whether there is a garden to continue; null until that has been looked up. */
    val hasSavedGarden: StateFlow<Boolean?> = _hasSavedGarden.asStateFlow()

    private val _atlas = MutableStateFlow(SpriteAtlas.EMPTY)
    val atlas: StateFlow<SpriteAtlas> = _atlas.asStateFlow()

    private val _terrain = MutableStateFlow(TerrainTextures.EMPTY)
    val terrain: StateFlow<TerrainTextures> = _terrain.asStateFlow()

    private var busy = false

    init {
        viewModelScope.launch { _hasSavedGarden.value = gardenRepository.hasSavedGarden() }
        // The same artwork the garden uses; it stays loaded for the garden afterwards.
        viewModelScope.launch { _atlas.value = assetRepository.loadAtlas() }
        viewModelScope.launch { _terrain.value = assetRepository.loadTerrainTextures() }
    }

    /**
     * Throws the saved garden away, then calls [onDone]: the garden screen sets up a new one
     * as it opens. Taps while this is under way are ignored.
     */
    fun startOver(onDone: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            gardenRepository.deleteSavedGarden()
            _hasSavedGarden.value = false
            onDone()
        }
    }
}
