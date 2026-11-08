package com.studioguu.miniaturegarden.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.DialogAction
import com.studioguu.miniaturegarden.ui.components.ParchmentDialog
import com.studioguu.miniaturegarden.ui.theme.GardenColors

/**
 * Asked before the first steps are played again from the settings (2026-11-08): it takes the
 * player to the practice garden at once, so it is not done on a single tap. The question is the
 * whole of it, with no title over it, and breaks between phrases ([MaterialTheme]'s styles), not
 * in the middle of a word. "やめる" or a tap outside leaves things as they are.
 */
@Composable
fun ReplayTutorialDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ParchmentDialog(
        onDismissRequest = onDismiss,
        title = null,
        actions = listOf(
            DialogAction(stringResource(R.string.replay_tutorial_cancel), onDismiss),
            DialogAction(stringResource(R.string.replay_tutorial_ok), onConfirm),
        ),
    ) {
        Text(
            stringResource(R.string.replay_tutorial_question),
            style = MaterialTheme.typography.titleMedium,
            color = GardenColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag(REPLAY_QUESTION_TAG),
        )
    }
}

const val REPLAY_QUESTION_TAG = "replay_tutorial_question"
