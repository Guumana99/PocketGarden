package com.studioguu.miniaturegarden.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studioguu.miniaturegarden.data.asset.AssetRepository
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CollectionViewModel @Inject constructor(
    gardenRepository: GardenRepository,
    assetRepository: AssetRepository,
) : ViewModel() {

    val progress: StateFlow<CollectionProgress> = gardenRepository.observeCollection()
        .map(::CollectionProgress)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionProgress(emptyList()))

    /** The same sprites the garden draws with, for the book's illustrations. */
    private val _atlas = MutableStateFlow(SpriteAtlas.EMPTY)
    val atlas: StateFlow<SpriteAtlas> = _atlas.asStateFlow()

    init {
        viewModelScope.launch { _atlas.value = assetRepository.loadAtlas() }
    }
}
