package com.studioguu.miniaturegarden.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class InventoryViewModel @Inject constructor(
    gardenRepository: GardenRepository,
) : ViewModel() {

    /** The player's slots in order, limited to the capacity they have unlocked. */
    val slots: StateFlow<List<InventorySlot>> =
        combine(gardenRepository.observeInventory(), gardenRepository.observeUserProfile()) { slots, profile ->
            if (profile == null) slots else slots.filter { it.slotIndex < profile.inventoryCapacity }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
