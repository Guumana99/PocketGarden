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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase
import com.studioguu.miniaturegarden.ui.objectDisplayName
import com.studioguu.miniaturegarden.ui.statusLabel
import kotlin.math.roundToInt

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
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
            }
        }
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
