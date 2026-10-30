package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.usecase.RemoveObjectUseCase
import com.studioguu.miniaturegarden.domain.usecase.StoreObjectUseCase
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.ui.objectDisplayName

/**
 * Asked before anything is taken off the garden, so that a stray tap on "remove" costs nothing.
 *
 * What can be put back into the bag (everything that was placed from an item) offers that as the
 * first choice, and taking it away ("撤去") as the second. A crop can only be pulled up ("抜く"),
 * and tilled soil only turned back into the ground it was ("戻す") (device feedback, after #75: one
 * word for each, and no word of throwing away). Cancelling, or tapping outside, leaves everything as it is.
 */
@Composable
fun RemoveConfirmDialog(
    gridObject: GridObject,
    onStore: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val item = StoreObjectUseCase.itemFor(gridObject)
    val returnable = item != null
    // A finished building has to be taken apart first; one still going up is just called off.
    val seconds = if (gridObject.isWorking) 0 else item?.demolishSeconds ?: 0
    val explanation = when {
        returnable -> R.string.remove_text_returnable
        gridObject.isHoedSoil -> R.string.remove_text_soil
        // What clearing it really does: a withered crop of the bed becomes compost; a log (#68) does not.
        RemoveObjectUseCase.leavesCompost(gridObject) -> R.string.remove_text_withered
        gridObject.status == PlantStatus.WITHERED -> R.string.remove_text_withered_log
        else -> null
    }
    val name = objectDisplayName(gridObject.objectId)
    val title = when {
        gridObject.isHoedSoil -> stringResource(R.string.remove_title_soil, name)
        gridObject.isPlant && gridObject.status == PlantStatus.WITHERED -> stringResource(R.string.remove_title_withered, name)
        gridObject.isPlant -> stringResource(R.string.remove_title_plant, name)
        else -> stringResource(R.string.remove_title, name)
    }
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (explanation != null) Text(stringResource(explanation))
                if (seconds > 0) {
                    Text(stringResource(R.string.remove_text_takes_time, durationText(seconds)))
                } else if (gridObject.workKind == WorkKind.BUILDING) {
                    Text(stringResource(R.string.remove_text_cancel_build))
                }
            }
        },
        // The choices are stacked: the safe one on top, the way out at the bottom.
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (returnable) {
                    WoodButton(
                        text = stringResource(R.string.remove_store),
                        onClick = onStore,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                WoodButton(
                    text = stringResource(
                        when {
                            gridObject.isHoedSoil -> R.string.remove_soil
                            gridObject.isPlant -> R.string.remove_pull
                            else -> R.string.remove_discard
                        },
                    ),
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth(),
                )
                WoodButton(
                    text = stringResource(R.string.remove_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/** Asked before the ground under [plant] is turned back, when the plant goes with it ([Soil.takesPlantBack]). */
@Composable
fun TurnBackConfirmDialog(plant: GridObject, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.turn_back_title, objectDisplayName(plant.objectId))) },
        text = { Text(stringResource(R.string.remove_text_soil)) },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WoodButton(text = stringResource(R.string.remove_soil), onClick = onConfirm, modifier = Modifier.fillMaxWidth())
                WoodButton(text = stringResource(R.string.remove_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}
