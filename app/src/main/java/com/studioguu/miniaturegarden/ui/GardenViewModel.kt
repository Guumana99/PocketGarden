package com.studioguu.miniaturegarden.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.ui.render.TileCoord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GardenViewModel @Inject constructor(
    private val gardenRepository: GardenRepository,
) : ViewModel() {

    val gridObjects: StateFlow<List<GridObject>> = gardenRepository.observeGridObjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedTile = MutableStateFlow<TileCoord?>(null)
    val selectedTile: StateFlow<TileCoord?> = _selectedTile.asStateFlow()

    init {
        viewModelScope.launch { gardenRepository.ensureInitialized() }
    }

    /** Tapping the selected tile again, or anywhere off the grid, clears the selection. */
    fun onTileTapped(tile: TileCoord?) {
        _selectedTile.value = tile.takeIf { it != _selectedTile.value }
    }
}
