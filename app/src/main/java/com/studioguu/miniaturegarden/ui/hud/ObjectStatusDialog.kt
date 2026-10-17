package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.ExpansionCheck
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.PlantSurroundings
import com.studioguu.miniaturegarden.domain.model.StructureEffect
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.usecase.MoveObjectUseCase
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.objectDisplayName
import com.studioguu.miniaturegarden.ui.statusLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Details and actions for the object on the selected tile.
 *
 * Shown as a floating panel rather than a modal dialog, so the garden and the quick action
 * bar stay usable while it is open.
 */
@Composable
fun ObjectStatusDialog(
    gridObject: GridObject,
    onWater: () -> Unit,
    onHarvest: () -> Unit,
    onRemove: () -> Unit,
    onRevive: () -> Unit,
    onMove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    surroundings: PlantSurroundings = PlantSurroundings.OPEN_GROUND,
    gardenSize: Int = GridObject.GRID_SIZE,
    playerLevel: Int = 1,
    onExpand: () -> Unit = {},
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(objectDisplayName(gridObject.objectId), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.tile_position, gridObject.gridX, gridObject.gridY),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (gridObject.isPlant) {
                    Text(
                        text = stringResource(statusLabel(gridObject.status)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (gridObject.status == PlantStatus.HEALTHY) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }

            if (gridObject.isWorking) WorkRow(gridObject)

            if (gridObject.isPlant) {
                LevelRow(stringResource(R.string.label_growth), gridObject.growthProgress)
                LevelRow(stringResource(R.string.label_moisture), gridObject.moistureLevel)
                LevelRow(stringResource(R.string.label_nutrition), gridObject.nutritionLevel)
                // What is left before it withers.
                LevelRow(stringResource(R.string.label_vitality), 1f - gridObject.stressRate)
            }
            val hint = when {
                gridObject.isPlant && gridObject.status == PlantStatus.WITHERED -> R.string.hint_withered
                gridObject.isPlant && gridObject.growthProgress >= 1f -> R.string.hint_overripe
                gridObject.isComposted -> R.string.hint_composted
                gridObject.isPlant && PlantSpecies.forObjectId(gridObject.objectId).isRepeatable -> R.string.hint_repeatable
                else -> null
            }
            if (hint != null) {
                Text(
                    text = stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // What the buildings nearby do for this plant, or what this building does for plants.
            val effects = buildList {
                if (surroundings.moistureDrainFactor < 1f) add(R.string.hint_near_well)
                if (surroundings.growthFactor > 1f) add(R.string.hint_near_hut)
                if (!gridObject.isWorking) {
                    when (StructureEffect.forObjectId(gridObject.objectId)) {
                        StructureEffect.WELL -> add(R.string.hint_well_effect)
                        StructureEffect.HUT -> add(R.string.hint_hut_effect)
                        null -> Unit
                    }
                }
            }
            for (effect in effects) {
                Text(
                    text = stringResource(effect),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            // The symbol building is where the garden is made larger from.
            if (gridObject.isSymbol && !gridObject.isWorking) {
                ExpansionRow(gardenSize, playerLevel, gridObject, onExpand)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (gridObject.isPlant && gridObject.status == PlantStatus.WITHERED) {
                    Button(onClick = onRevive) {
                        Text(stringResource(R.string.action_revive, CareForPlantUseCase.REVIVE_COST))
                    }
                } else if (gridObject.isPlant) {
                    val alive = gridObject.status != PlantStatus.WITHERED
                    FilledTonalButton(onClick = onWater, enabled = alive) {
                        Text(stringResource(R.string.action_water))
                    }
                    Button(onClick = onHarvest, enabled = alive && gridObject.growthProgress >= 1f) {
                        val reward = PlantSpecies.forObjectId(gridObject.objectId).harvestReward(gridObject.harvestCount)
                        Text(stringResource(R.string.action_harvest, reward.coins))
                    }
                }
                if (MoveObjectUseCase.canMove(gridObject) && !gridObject.isWorking) {
                    FilledTonalButton(onClick = onMove) { Text(stringResource(R.string.action_move)) }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
            }
        }
    }
}

/** Growing the garden: the button when it can grow now, otherwise what it still needs. */
@Composable
private fun ExpansionRow(gardenSize: Int, playerLevel: Int, symbol: GridObject, onExpand: () -> Unit) {
    val stage = GardenExpansion.nextStage(gardenSize)
    when (GardenExpansion.check(gardenSize, listOf(symbol), playerLevel)) {
        ExpansionCheck.READY -> WoodButton(
            text = stringResource(R.string.expand_button, stage?.size ?: gardenSize),
            onClick = onExpand,
        )
        ExpansionCheck.NEEDS_LEVEL -> if (stage != null) {
            Text(
                text = stringResource(R.string.expand_needs_level, stage.playerLevel, stage.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExpansionCheck.AT_MAX_SIZE -> Text(
            text = stringResource(R.string.expand_max),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Not possible here: the panel is the symbol building's own.
        ExpansionCheck.NEEDS_SYMBOL -> Unit
    }
}

/** What is being done to a work site, how far along it is and how long it still takes. */
@Composable
private fun WorkRow(gridObject: GridObject) {
    // The job's own clock: looked at twice a second while the panel is open.
    val now by produceState(System.currentTimeMillis(), gridObject.instanceId) {
        while (true) {
            delay(500)
            value = System.currentTimeMillis()
        }
    }
    val label = when (gridObject.workKind) {
        WorkKind.DEMOLISHING -> R.string.work_demolishing
        WorkKind.PACKING -> R.string.work_packing
        else -> R.string.work_building
    }
    if (gridObject.workKind == WorkKind.READY) {
        Text(stringResource(R.string.work_ready), style = MaterialTheme.typography.labelLarge)
        return
    }
    val secondsLeft = ((gridObject.workRemainingMillis(now) + 999) / 1000).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.work_remaining, durationText(secondsLeft)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(progress = { gridObject.workProgress(now) }, modifier = Modifier.fillMaxWidth())
    }
}

/** A length of time in words: "45秒", "2分", "2分30秒". */
@Composable
fun durationText(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return when {
        minutes == 0 -> stringResource(R.string.duration_seconds, rest)
        rest == 0 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_minutes_seconds, minutes, rest)
    }
}

@Composable
private fun LevelRow(label: String, level: Float) {
    val clamped = level.coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
        LinearProgressIndicator(progress = { clamped }, modifier = Modifier.weight(1f).fillMaxWidth())
        Text(
            text = stringResource(R.string.label_percent, (clamped * 100).roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(44.dp),
        )
    }
}
