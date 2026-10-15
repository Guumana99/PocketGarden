package com.studioguu.miniaturegarden.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.ui.itemDisplayName
import com.studioguu.miniaturegarden.ui.itemEmoji

private const val COLUMNS = 4

/**
 * The player's items in a grid. Tapping a filled slot picks it for placement.
 *
 * The slots are read before the sheet opens and handed in, and the sheet opens straight to its
 * full height: had it to wait for them, it would grow taller while still sliding up, which shows
 * as a stutter.
 *
 * @param slots the slots the player has, in order.
 * @param onSlotSelected called with the chosen slot's index; the caller closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryBottomSheet(
    slots: List<InventorySlot>,
    onSlotSelected: (slotIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.inventory_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.inventory_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                contentPadding = PaddingValues(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(slots, key = { it.slotIndex }) { slot ->
                    SlotCell(slot, onClick = { onSlotSelected(slot.slotIndex) })
                }
            }
        }
    }
}

@Composable
private fun SlotCell(slot: InventorySlot, onClick: () -> Unit) {
    val item = if (slot.isEmpty) null else GardenItem.fromItemId(slot.itemId)
    val name = item?.let { itemDisplayName(it) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (item != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics { contentDescription = "$name ×${slot.amount}" }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (item != null) {
            Text(itemEmoji(item), fontSize = 30.sp)
            Text(
                text = "×${slot.amount}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
        }
    }
}
