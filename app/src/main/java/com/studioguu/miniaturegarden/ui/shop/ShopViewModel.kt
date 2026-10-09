package com.studioguu.miniaturegarden.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.usecase.ActionResult
import com.studioguu.miniaturegarden.domain.usecase.PurchaseItemUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of the most recent purchase attempt, shown inside the shop. */
data class PurchaseFeedback(val item: GardenItem, val result: ActionResult)

@HiltViewModel
class ShopViewModel @Inject constructor(
    gardenRepository: GardenRepository,
    private val purchaseItem: PurchaseItemUseCase,
) : ViewModel() {

    val items: List<GardenItem> = GardenItem.entries

    val coins: StateFlow<Long> = gardenRepository.observeUserProfile()
        .map { it?.coins ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _feedback = MutableStateFlow<PurchaseFeedback?>(null)
    val feedback: StateFlow<PurchaseFeedback?> = _feedback.asStateFlow()

    fun buy(item: GardenItem) {
        viewModelScope.launch { _feedback.value = PurchaseFeedback(item, purchaseItem(item)) }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}
