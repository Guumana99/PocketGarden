package com.studioguu.miniaturegarden.ui.hud

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.usecase.RemoveObjectUseCase
import com.studioguu.miniaturegarden.domain.usecase.StoreObjectUseCase
import com.studioguu.miniaturegarden.ui.components.DialogAction
import com.studioguu.miniaturegarden.ui.components.ParchmentDialog
import com.studioguu.miniaturegarden.ui.objectDisplayName
import com.studioguu.miniaturegarden.ui.theme.GardenColors

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
    val discard = DialogAction(
        stringResource(
            when {
                gridObject.isHoedSoil -> R.string.remove_soil
                gridObject.isPlant -> R.string.remove_pull
                else -> R.string.remove_discard
            },
        ),
        onDiscard,
    )
    val cancel = DialogAction(stringResource(R.string.remove_cancel), onDismiss)
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = title,
        // With three, the safe one on top and the way out at the bottom.
        actions = if (returnable) listOf(DialogAction(stringResource(R.string.remove_store), onStore), discard, cancel) else listOf(cancel, discard),
    ) {
        if (explanation != null) Text(stringResource(explanation), color = GardenColors.Ink)
        if (seconds > 0) {
            Text(stringResource(R.string.remove_text_takes_time, durationText(seconds)), color = GardenColors.Ink)
        } else if (gridObject.workKind == WorkKind.BUILDING) {
            Text(stringResource(R.string.remove_text_cancel_build), color = GardenColors.Ink)
        }
    }
}

/** Asked before the ground under [plant] is turned back, when the plant goes with it ([Soil.takesPlantBack]). */
@Composable
fun TurnBackConfirmDialog(plant: GridObject, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.turn_back_title, objectDisplayName(plant.objectId)),
        actions = listOf(
            DialogAction(stringResource(R.string.remove_cancel), onDismiss),
            DialogAction(stringResource(R.string.remove_soil), onConfirm),
        ),
    ) {
        Text(stringResource(R.string.remove_text_soil), color = GardenColors.Ink)
    }
}
