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
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.usecase.MoveObjectUseCase
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase
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
                else -> null
            }
            if (hint != null) {
                Text(
                    text = stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        Text(stringResource(R.string.action_harvest, PlantSpecies.forObjectId(gridObject.objectId).harvestCoins))
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
