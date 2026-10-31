package com.studioguu.miniaturegarden.ui.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.InventoryFilter
import com.studioguu.miniaturegarden.domain.model.InventoryOrder
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.InventorySort
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.ui.itemDisplayName
import com.studioguu.miniaturegarden.ui.ItemIcon
import com.studioguu.miniaturegarden.ui.seasonLabel
import com.studioguu.miniaturegarden.ui.theme.GardenColors

private const val COLUMNS = 4

/**
 * The player's items in a grid. Tapping a filled slot picks it for placement.
 *
 * The slots are read before the sheet opens and handed in, and the sheet opens straight to its
 * full height: had it to wait for them, it would grow taller while still sliding up, which shows
 * as a stutter.
 *
 * Tabs along the top narrow it down to one kind of item, and the sort button beside the title
 * puts what is left in order ([InventoryOrder]). Whatever the tab, the grid keeps every empty
 * slot, so the sheet does not change height. Switching tabs or orders swaps the cells in place at
 * once: the cells carry no item animation, as items sliding across the grid read as a stutter.
 *
 * @param slots the slots the player has, in slot order.
 * @param season the garden's season, for sorting by season.
 * @param notice said in place of the line under the title, so that nothing below it moves: why
 *   the seed just tapped stays in the bag.
 * @param onSlotSelected called with the chosen slot's index; the caller closes the sheet, or
 *   leaves it open with a [notice].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryBottomSheet(
    slots: List<InventorySlot>,
    sort: InventorySort,
    onSortChange: (InventorySort) -> Unit,
    filter: InventoryFilter,
    onFilterChange: (InventoryFilter) -> Unit,
    season: Season,
    onSlotSelected: (slotIndex: Int) -> Unit,
    onDismiss: () -> Unit,
    notice: String? = null,
) {
    val arranged = remember(slots, sort, filter, season) { InventoryOrder.arrange(slots, sort, filter, season) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.inventory_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = notice ?: stringResource(R.string.inventory_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (notice != null) GardenColors.WoodDark else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (notice != null) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                SortButton(sort, onSortChange)
            }
            FilterTabs(filter, onFilterChange, modifier = Modifier.padding(top = 12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(arranged, key = { it.slotIndex }) { slot ->
                    SlotCell(slot, season, onClick = { onSlotSelected(slot.slotIndex) })
                }
                // The places of the items another tab holds, left as plain cells, so that the grid
                // stays whole and the same size under every tab.
                items(slots.size - arranged.size, key = { "hidden_$it" }) {
                    SlotCell(InventorySlot(slotIndex = -1), season, onClick = {})
                }
            }
        }
    }
}

/** The name of a sort order, as the button and its menu show it. */
@Composable
private fun sortLabel(sort: InventorySort): String = stringResource(
    when (sort) {
        InventorySort.ACQUIRED -> R.string.inventory_sort_acquired
        InventorySort.KIND -> R.string.inventory_sort_kind
        InventorySort.AMOUNT -> R.string.inventory_sort_amount
        InventorySort.SEASON -> R.string.inventory_sort_season
        InventorySort.LEVEL -> R.string.inventory_sort_level
    }
)

/** A small wooden tag with the current order on it; a tap opens the list of orders. */
@Composable
private fun SortButton(sort: InventorySort, onSortChange: (InventorySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.inventory_sort_description, sortLabel(sort))
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(50),
            color = GardenColors.Wood,
            border = BorderStroke(2.dp, GardenColors.WoodDark),
            shadowElevation = 2.dp,
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("⇅", color = GardenColors.Cream, fontWeight = FontWeight.Bold)
                Text(sortLabel(sort), color = GardenColors.Cream, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text("▾", color = GardenColors.WoodLight, style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = GardenColors.Parchment,
            border = BorderStroke(1.dp, GardenColors.WoodLight),
        ) {
            InventorySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            sortLabel(option),
                            color = GardenColors.Ink,
                            fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = { Text(if (option == sort) "✓" else "", color = GardenColors.Wood, fontWeight = FontWeight.Bold) },
                    onClick = {
                        open = false
                        onSortChange(option)
                    },
                )
            }
        }
    }
}

/** The tabs that narrow the bag down to one kind of item: rounded wooden tags in a row. */
@Composable
private fun FilterTabs(filter: InventoryFilter, onFilterChange: (InventoryFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        InventoryFilter.entries.forEach { option ->
            val selected = option == filter
            Surface(
                onClick = { onFilterChange(option) },
                shape = RoundedCornerShape(50),
                color = if (selected) GardenColors.WoodLight else Color.Transparent,
                border = BorderStroke(1.5.dp, if (selected) GardenColors.Wood else GardenColors.WoodLight),
                modifier = Modifier.weight(1f).semantics { this.selected = selected },
            ) {
                Text(
                    text = stringResource(
                        when (option) {
                            InventoryFilter.ALL -> R.string.inventory_filter_all
                            InventoryFilter.SEEDS -> R.string.inventory_filter_seeds
                            InventoryFilter.FURNITURE -> R.string.inventory_filter_furniture
                            InventoryFilter.OTHER -> R.string.inventory_filter_other
                        }
                    ),
                    color = if (selected) GardenColors.Ink else GardenColors.InkSoft,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SlotCell(slot: InventorySlot, season: Season, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val item = if (slot.isEmpty) null else GardenItem.fromItemId(slot.itemId)
    // A seed out of its season rests in the bag, with the season it waits for beside it.
    val resting = item != null && InventoryOrder.isOutOfSeason(item, season)
    val name = item?.let { itemDisplayName(it) }
    Box(
        modifier = modifier
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
            ItemIcon(item, 30.sp, modifier = if (resting) Modifier.alpha(RESTING_ALPHA) else Modifier)
            if (resting) {
                Text(
                    text = stringResource(seasonLabel(InventoryOrder.nextSeason(item, season))),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                )
            }
            Text(
                text = "×${slot.amount}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
        }
    }
}

/** How faint a seed out of its season is drawn. */
private const val RESTING_ALPHA = 0.45f
