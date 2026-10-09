package com.studioguu.miniaturegarden.domain.model

data class InventorySlot(
    val slotIndex: Int,
    val itemId: String = EMPTY_ITEM_ID,
    val amount: Int = 0,
) {
    val isEmpty: Boolean get() = itemId == EMPTY_ITEM_ID || amount <= 0

    companion object {
        const val EMPTY_ITEM_ID = ""
        const val MAX_STACK = 64
    }
}
