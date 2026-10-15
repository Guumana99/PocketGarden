package com.studioguu.miniaturegarden.domain.model

data class InventorySlot(
    val slotIndex: Int,
    val itemId: String = EMPTY_ITEM_ID,
    val amount: Int = 0,
    /**
     * When it last came into the bag, as a running number: higher is more recent. Bumped each time
     * something is put into the slot (bought, taken back from the garden, given), not when some is
     * taken out. 0 for slots filled before this was kept, which then count as the oldest.
     */
    val acquiredOrder: Long = 0L,
) {
    val isEmpty: Boolean get() = itemId == EMPTY_ITEM_ID || amount <= 0

    companion object {
        const val EMPTY_ITEM_ID = ""
        const val MAX_STACK = 64
    }
}
