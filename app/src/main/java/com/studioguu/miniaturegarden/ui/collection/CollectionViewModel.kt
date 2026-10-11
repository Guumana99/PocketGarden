package com.studioguu.miniaturegarden.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CollectionViewModel @Inject constructor(
    gardenRepository: GardenRepository,
) : ViewModel() {

    val progress: StateFlow<CollectionProgress> = gardenRepository.observeCollection()
        .map(::CollectionProgress)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionProgress(emptyList()))
}
