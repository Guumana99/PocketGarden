package com.studioguu.miniaturegarden.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.StarterGarden
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.ui.itemDisplayName
import com.studioguu.miniaturegarden.ui.itemEmoji
import com.studioguu.miniaturegarden.ui.objectDisplayName
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/**
 * The popup the starter crate opens into: a wooden board with a parchment inset, the seed it
 * holds, and a button to take it. "Later" leaves the crate where it is.
 */
@Composable
fun StarterCrateDialog(onReceive: () -> Unit, onDismiss: () -> Unit) {
    val reward = StarterGarden.CRATE_REWARD
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.popIn(),
            shape = RoundedCornerShape(14.dp),
            color = GardenColors.Wood,
            border = BorderStroke(3.dp, GardenColors.WoodDark),
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "📦 " + objectDisplayName(GridObject.STARTER_CRATE_OBJECT_ID),
                    color = GardenColors.Cream,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GardenColors.Parchment,
                    border = BorderStroke(1.dp, GardenColors.WoodLight),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.crate_text),
                            color = GardenColors.Ink,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(itemEmoji(reward), fontSize = 40.sp)
                        Text(
                            text = stringResource(R.string.crate_reward, itemDisplayName(reward), StarterGarden.CRATE_REWARD_AMOUNT),
                            color = GardenColors.Ink,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.crate_later), color = GardenColors.Cream)
                    }
                    WoodButton(text = stringResource(R.string.crate_receive), onClick = onReceive)
                }
            }
        }
    }
}

/** What the current first step asks for; null when there is nothing to say. */
@StringRes
fun tutorialHint(step: TutorialStep): Int? = when (step) {
    TutorialStep.OPEN_CRATE -> R.string.tutorial_hint_open_crate
    TutorialStep.PLANT_SEED -> R.string.tutorial_hint_plant_seed
    TutorialStep.WATER_SEEDLING -> R.string.tutorial_hint_water
    TutorialStep.DONE -> null
}

/**
 * A small wooden sign above the tool bar with the current first step. Like the other panels
 * there, it is laid over the garden and fades in and out in place.
 */
@Composable
fun TutorialHint(@StringRes text: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = GardenColors.Parchment.copy(alpha = 0.95f),
        border = BorderStroke(2.dp, GardenColors.WoodLight),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("✨", fontSize = 18.sp)
            Text(stringResource(text), color = GardenColors.Ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
