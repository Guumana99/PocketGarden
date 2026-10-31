package com.studioguu.miniaturegarden.ui.hud

import androidx.annotation.StringRes
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.BuildingPaint
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.PlantSurroundings
import com.studioguu.miniaturegarden.domain.model.SoilGrade
import com.studioguu.miniaturegarden.domain.model.StructureEffect
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.usecase.GardenLayout
import com.studioguu.miniaturegarden.domain.usecase.LayoutAction
import com.studioguu.miniaturegarden.domain.usecase.LayoutCheck
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.objectDisplayName
import com.studioguu.miniaturegarden.ui.statusLabel
import com.studioguu.miniaturegarden.ui.soilGradeName
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Details and actions for the object on the selected tile.
 *
 * Shown as a floating panel rather than a modal dialog, so the garden and the quick action
 * bar stay usable while it is open.
 *
 * @param editing edit mode is on: a piece of the garden's layout can be moved and taken off
 *   here only then ([GardenLayout]); otherwise the panel says where that is done.
 * @param guiding the first steps are under way: the panel shows the plant and its watering
 *   only, without harvesting, clearing away or the notes underneath.
 * @param soil the cell of soil it is, or that it stands in: its grade is shown ([SoilGrade]).
 * @param endsAtHarvest it is past its season, and its next harvest will be its last.
 * @param onTurnBackGround turns the ground under a plant back a step, in edit mode.
 *   harvest, when above 0.
 * @param sprinkled a running sprinkler waters it.
 * @param onToggleSprinkler switches the sprinkler on or off; its button is shown for a sprinkler.
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
    onFlip: () -> Unit = {},
    /** Opens the choice of colours, for a building that can be painted. */
    onPaint: () -> Unit = {},
    modifier: Modifier = Modifier,
    surroundings: PlantSurroundings = PlantSurroundings.OPEN_GROUND,
    editing: Boolean = false,
    guiding: Boolean = false,
    soil: GridObject? = null,
    endsAtHarvest: Boolean = false,
    onTurnBackGround: () -> Unit = {},
    sprinkled: Boolean = false,
    onToggleSprinkler: () -> Unit = {},
    /** How far the game's clock is ahead of the device's: the time a job has left goes by the game's. */
    clockOffset: Long = 0L,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The header: name on the left, then the plant's state as a small tag, then the close
            // button, each with room of its own (the state used to sit right against the button).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HEADER_GAP),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(objectDisplayName(gridObject.objectId), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.tile_position, gridObject.gridX, gridObject.gridY),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (gridObject.isPlant) StatusTag(gridObject.status)
                WoodButton(text = stringResource(R.string.action_close), onClick = onDismiss)
            }

            if (gridObject.isWorking) WorkRow(gridObject, clockOffset)

            if (gridObject.isSprinkler) {
                Text(
                    text = stringResource(if (gridObject.isOn) R.string.sprinkler_running else R.string.sprinkler_stopped),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (gridObject.isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (soil != null && !guiding) {
                Text(
                    text = stringResource(soilGradeName(SoilGrade.of(soil.soilQuality))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (gridObject.isPlant) {
                LevelRow(stringResource(R.string.label_growth), gridObject.growthProgress)
                LevelRow(stringResource(R.string.label_moisture), gridObject.moistureLevel)
                // The food in its soil; a log takes none.
                if (PlantSpecies.forObjectId(gridObject.objectId).ground.usesSoil) {
                    LevelRow(stringResource(R.string.label_nutrition), gridObject.nutritionLevel)
                }
                // What is left before it withers.
                LevelRow(stringResource(R.string.label_vitality), 1f - gridObject.stressRate)
            } else if (gridObject.isSoil) {
                LevelRow(stringResource(R.string.label_nutrition), gridObject.nutritionLevel)
            }
            val hint = when {
                // A log (#68) only lay on the soil: cleared away, it leaves no compost.
                gridObject.isPlant && gridObject.status == PlantStatus.WITHERED ->
                    if (PlantSpecies.forObjectId(gridObject.objectId).ground.usesSoil) R.string.hint_withered else R.string.hint_withered_log
                endsAtHarvest -> R.string.hint_season_over
                gridObject.isPlant && gridObject.growthProgress >= 1f -> R.string.hint_overripe
                gridObject.status == PlantStatus.UNNOURISHED -> R.string.hint_weak
                gridObject.isLooseSoil -> R.string.hint_loose_soil
                gridObject.isTilledSoil -> R.string.hint_ridged_soil
                gridObject.isPlant && PlantSpecies.forObjectId(gridObject.objectId).isRepeatable -> R.string.hint_repeatable
                else -> null
            }
            if (hint != null && !guiding) {
                Text(
                    text = stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // What the buildings nearby do for this plant, or what this building does for plants.
            val effects = buildList {
                if (sprinkled) {
                    add(R.string.hint_near_sprinkler)
                } else if (surroundings.moistureDrainFactor < 1f) {
                    add(R.string.hint_near_well)
                }
                if (gridObject.isSprinkler) add(if (gridObject.isOn) R.string.hint_sprinkler_effect else R.string.hint_sprinkler_stopped)
                if (surroundings.growthFactor > 1f) add(R.string.hint_near_hut)
                if (!gridObject.isWorking) {
                    when (StructureEffect.forObjectId(gridObject.objectId)) {
                        StructureEffect.WELL -> add(R.string.hint_well_effect)
                        StructureEffect.HUT -> add(R.string.hint_hut_effect)
                        StructureEffect.WINDMILL -> add(R.string.hint_windmill_effect)
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

            // Outside edit mode a piece of the layout stays put; say where it can be rearranged.
            val arrangeable = GardenLayout.check(LayoutAction.MOVE, gridObject, editing = true) == LayoutCheck.OK ||
                GardenLayout.check(LayoutAction.STORE, gridObject, editing = true) == LayoutCheck.OK
            if (!editing && GardenLayout.isLayoutPiece(gridObject) && arrangeable) {
                Text(
                    text = stringResource(R.string.hint_edit_to_arrange),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!editing && !guiding && (gridObject.isHoedSoil || (gridObject.isPlant && gridObject.status == PlantStatus.WITHERED))) {
                // What there is to clear away (a withered crop, an empty bed) is cleared in edit mode.
                Text(
                    text = stringResource(R.string.hint_edit_to_clear),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Free, at any time: in the everyday garden as well as in edit mode.
            if (BuildingPaint.canPaint(gridObject.objectId) && !gridObject.isWorking) {
                FilledTonalButton(onClick = onPaint, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_paint)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Edit mode keeps to rearranging (#50): no watering, harvesting or reviving there.
                if (editing) {
                    Unit
                } else if (gridObject.isSprinkler) {
                    // Switched where it stands, at a tap, outside edit mode like any care.
                    WoodButton(
                        text = stringResource(if (gridObject.isOn) R.string.action_sprinkler_stop else R.string.action_sprinkler_start),
                        onClick = onToggleSprinkler,
                    )
                } else if (gridObject.isPlant && gridObject.status == PlantStatus.WITHERED) {
                    Button(onClick = onRevive) {
                        Text(stringResource(R.string.action_revive, CareForPlantUseCase.REVIVE_COST))
                    }
                } else if (gridObject.isPlant) {
                    val alive = gridObject.status != PlantStatus.WITHERED
                    FilledTonalButton(onClick = onWater, enabled = alive) {
                        Text(stringResource(R.string.action_water))
                    }
                    if (!guiding) {
                        Button(onClick = onHarvest, enabled = alive && gridObject.growthProgress >= 1f) {
                            Text(stringResource(R.string.action_harvest))
                        }
                    }
                }
                if (GardenLayout.check(LayoutAction.MOVE, gridObject, editing) == LayoutCheck.OK && !gridObject.isWorking) {
                    FilledTonalButton(onClick = onMove) { Text(stringResource(R.string.action_move)) }
                }
                // A building is turned round where it stands, once it stands: a work site has no front (#50).
                if (gridObject.canFlip && !gridObject.isWorking) {
                    FilledTonalButton(onClick = onFlip) { Text(stringResource(R.string.action_flip)) }
                }
                Spacer(Modifier.weight(1f))
                // Anything is cleared away in edit mode only (#49).
                if (!guiding && GardenLayout.check(LayoutAction.DISCARD, gridObject, editing) == LayoutCheck.OK) {
                    // The ground under a plant is turned back from here too, with or without it.
                    if (gridObject.isPlant && soil?.isHoedSoil == true) {
                        OutlinedButton(onClick = onTurnBackGround) { Text(stringResource(R.string.action_turn_back_ground)) }
                    }
                    OutlinedButton(onClick = onRemove) { Text(stringResource(removeLabel(gridObject))) }
                }
            }
        }
    }
}

/** Room between the name, the plant's state and the close button in the panel's header. */
internal val HEADER_GAP = 12.dp

/** A plant's state ("元気", "のどがかわいた"…) as a small tinted tag, set apart from the close button. */
@Composable
private fun StatusTag(status: PlantStatus) {
    val colour = if (status == PlantStatus.HEALTHY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(shape = RoundedCornerShape(50), color = colour.copy(alpha = 0.12f)) {
        Text(
            text = stringResource(statusLabel(status)),
            style = MaterialTheme.typography.labelLarge,
            color = colour,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** What is being done to a work site, how far along it is and how long it still takes. */
@Composable
private fun WorkRow(gridObject: GridObject, clockOffset: Long) {
    // The job's own clock: looked at twice a second while the panel is open.
    val now by produceState(System.currentTimeMillis() + clockOffset, gridObject.instanceId, clockOffset) {
        while (true) {
            delay(500)
            value = System.currentTimeMillis() + clockOffset
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

/** A length of time in words: "45秒", "2分", "2分30秒", "1時間", "1時間45分". */
@Composable
fun durationText(seconds: Int): String {
    val spoken = SpokenDuration.of(seconds)
    return when {
        spoken.hours > 0 && spoken.minutes == 0 -> stringResource(R.string.duration_hours, spoken.hours)
        spoken.hours > 0 -> stringResource(R.string.duration_hours_minutes, spoken.hours, spoken.minutes)
        spoken.minutes == 0 -> stringResource(R.string.duration_seconds, spoken.seconds)
        spoken.seconds == 0 -> stringResource(R.string.duration_minutes, spoken.minutes)
        else -> stringResource(R.string.duration_minutes_seconds, spoken.minutes, spoken.seconds)
    }
}

/**
 * How a length of time is said. Under an hour, in minutes and seconds. From an hour on, in hours
 * and minutes, with the seconds rounded up into the next minute, so that a wait never reads
 * shorter than it is ("1時間" stays until the last hour has truly begun).
 */
data class SpokenDuration(val hours: Int, val minutes: Int, val seconds: Int) {
    companion object {
        fun of(totalSeconds: Int): SpokenDuration {
            val total = totalSeconds.coerceAtLeast(0)
            if (total < 3_600) return SpokenDuration(0, total / 60, total % 60)
            val minutesUp = (total + 59) / 60
            return SpokenDuration(minutesUp / 60, minutesUp % 60, 0)
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

/** What taking [gridObject] off the garden is called: a crop is pulled up, a bed turned back into ground, anything else removed. */
@StringRes
fun removeLabel(gridObject: GridObject): Int = when {
    gridObject.isHoedSoil -> R.string.action_restore_ground
    gridObject.isPlant -> R.string.action_pull
    else -> R.string.action_remove
}
