package com.studioguu.miniaturegarden.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.LetterKind
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.ui.seasonEmoji
import com.studioguu.miniaturegarden.ui.seasonLabel
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The garden's post: a wooden board with the letters that have come, newest first, each on its
 * own sheet of parchment. A letter not read before wears a small mark. Opening the post counts
 * as reading everything in it ([onOpened]).
 */
@Composable
fun PostDialog(letters: List<Letter>, onOpened: () -> Unit, onDismiss: () -> Unit) {
    // What was new when the post was opened keeps its mark while it is open.
    val unread = remember { letters.filter { !it.read }.map { it.letterId }.toSet() }
    LaunchedEffect(Unit) { onOpened() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.popIn(),
            shape = RoundedCornerShape(14.dp),
            color = GardenColors.Wood,
            border = BorderStroke(3.dp, GardenColors.WoodDark),
            shadowElevation = 10.dp,
        ) {
            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "📮 " + stringResource(R.string.post_title),
                    color = GardenColors.Cream,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (letters.isEmpty()) {
                        Text(
                            stringResource(R.string.post_empty),
                            color = GardenColors.Cream,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                    for (letter in letters) LetterSheet(letter, isNew = letter.letterId in unread)
                }
                WoodButton(
                    text = stringResource(R.string.action_close),
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** One letter: when it came, a title and a few warm lines. */
@Composable
private fun LetterSheet(letter: Letter, isNew: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = GardenColors.Parchment,
        border = BorderStroke(1.dp, GardenColors.WoodLight),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = letterTitle(letter),
                    color = GardenColors.Ink,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (isNew) {
                    Text(stringResource(R.string.post_new), color = GardenColors.Wood, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(letterBody(letter), color = GardenColors.Ink, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = DATE.format(Instant.ofEpochMilli(letter.sentAt).atZone(ZoneId.systemDefault())),
                color = GardenColors.InkSoft,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

@Composable
private fun letterTitle(letter: Letter): String {
    val name = stringResource(seasonLabel(letter.season))
    return when (letter.kind) {
        LetterKind.SEASON_ARRIVED -> stringResource(R.string.letter_arrived_title, seasonEmoji(letter.season), name)
        LetterKind.SEASON_COMING -> stringResource(R.string.letter_coming_title, seasonEmoji(letter.season), name)
    }
}

@Composable
private fun letterBody(letter: Letter): String {
    val name = stringResource(seasonLabel(letter.season))
    return when (letter.kind) {
        LetterKind.SEASON_ARRIVED -> stringResource(seasonScene(letter.season)) + "\n" + stringResource(R.string.letter_arrived_body, name)
        LetterKind.SEASON_COMING -> stringResource(R.string.letter_coming_body, name)
    }
}

/** A line about how the garden looks as [season] comes. */
private fun seasonScene(season: Season): Int = when (season) {
    Season.SPRING -> R.string.letter_scene_spring
    Season.SUMMER -> R.string.letter_scene_summer
    Season.AUTUMN -> R.string.letter_scene_autumn
    Season.WINTER -> R.string.letter_scene_winter
}
