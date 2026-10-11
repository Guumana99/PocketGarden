package com.studioguu.miniaturegarden.ui.collection

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.ui.itemEmoji
import com.studioguu.miniaturegarden.ui.objectDisplayName

/**
 * The garden encyclopedia: every plant and object there is, filled in as the player harvests or
 * places each one for the first time. Its own text is written the way an old cartridge game
 * would have put it, in kana with spaces.
 */
@Composable
fun CollectionDialog(
    onDismiss: () -> Unit,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collection_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.collection_progress, progress.discovered, progress.total, progress.percent),
                    style = MaterialTheme.typography.titleSmall,
                )
                LinearProgressIndicator(
                    progress = { progress.discovered.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (progress.isComplete) {
                        stringResource(R.string.collection_complete)
                    } else {
                        stringResource(
                            R.string.collection_complete_reward,
                            CollectionEntry.COMPLETION_COINS,
                            CollectionEntry.COMPLETION_XP,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (entry in CollectionEntry.entries) {
                    EntryCard(entry, progress.recordOf(entry))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun EntryCard(entry: CollectionEntry, record: CollectionRecord?) {
    val discovered = record != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(if (discovered) entryEmoji(entry) else "❔", fontSize = 30.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (discovered) objectDisplayName(entry.objectId) else stringResource(R.string.collection_unknown),
                style = MaterialTheme.typography.titleSmall,
            )
            if (record != null) {
                Text(stringResource(flavorText(entry)), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(
                        if (entry.trigger == CollectionTrigger.HARVEST) {
                            R.string.collection_count_harvest
                        } else {
                            R.string.collection_count_placement
                        },
                        record.count,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = stringResource(
                        if (entry.trigger == CollectionTrigger.HARVEST) {
                            R.string.collection_hint_harvest
                        } else {
                            R.string.collection_hint_placement
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.collection_discovery_reward, entry.discoveryCoins),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The shop item for the same object carries the emoji. */
private fun entryEmoji(entry: CollectionEntry): String =
    GardenItem.entries.firstOrNull { it.objectId == entry.objectId }?.let(::itemEmoji) ?: "❔"

@StringRes
private fun flavorText(entry: CollectionEntry): Int = when (entry) {
    CollectionEntry.TULIP -> R.string.flavor_tulip
    CollectionEntry.SUNFLOWER -> R.string.flavor_sunflower
    CollectionEntry.ROSE -> R.string.flavor_rose
    CollectionEntry.GRASS -> R.string.flavor_grass
    CollectionEntry.PEBBLE -> R.string.flavor_pebble
    CollectionEntry.ROCK -> R.string.flavor_rock
    CollectionEntry.RIVER -> R.string.flavor_river
    CollectionEntry.WELL -> R.string.flavor_well
    CollectionEntry.HUT -> R.string.flavor_hut
}
