package com.studioguu.miniaturegarden.ui.post

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.LetterKind
import com.studioguu.miniaturegarden.domain.model.LetterTip
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.ui.itemDisplayName
import com.studioguu.miniaturegarden.ui.itemEmoji
import com.studioguu.miniaturegarden.ui.seasonEmoji
import com.studioguu.miniaturegarden.ui.seasonLabel
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The red of the "NEW" stamp on a letter not read yet. */
private val StampRed = Color(0xFFD8442F)

/**
 * The garden's post: an archive of every letter that has come, newest first, to scroll back
 * through at any time. The list shows each letter's title and date; one not read yet is a sealed
 * envelope with a red "NEW" stamp. Tapping a letter opens its page, and only then is it put down
 * as read ([onRead]). Back, or the button under the page, returns to the list.
 */
@Composable
fun PostDialog(letters: List<Letter>, onRead: (letterId: String) -> Unit, onDismiss: () -> Unit) {
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    val opened = letters.firstOrNull { it.letterId == openedId }
    Dialog(onDismissRequest = { if (opened != null) openedId = null else onDismiss() }) {
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
                if (opened != null) {
                    LaunchedEffect(opened.letterId) { if (!opened.read) onRead(opened.letterId) }
                    LetterPage(opened)
                    WoodButton(
                        text = stringResource(R.string.post_back),
                        onClick = { openedId = null },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                } else {
                    LetterList(letters, onOpen = { openedId = it })
                    WoodButton(
                        text = stringResource(R.string.action_close),
                        onClick = onDismiss,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

/** Every letter, newest first, in a list that scrolls; how many are unread above it. */
@Composable
private fun LetterList(letters: List<Letter>, onOpen: (letterId: String) -> Unit) {
    if (letters.isEmpty()) {
        Text(
            stringResource(R.string.post_empty),
            color = GardenColors.Cream,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
        return
    }
    val unread = letters.count { !it.read }
    Text(
        text = if (unread > 0) stringResource(R.string.post_unread_count, unread) else stringResource(R.string.post_all_read),
        color = GardenColors.WoodLight,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    LazyColumn(
        modifier = Modifier.heightIn(max = 440.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(letters, key = { it.letterId }) { letter -> LetterRow(letter, onClick = { onOpen(letter.letterId) }) }
    }
}

/** One letter in the list: an envelope, its title and date, and the red stamp while unread. */
@Composable
private fun LetterRow(letter: Letter, onClick: () -> Unit) {
    val title = letterTitle(letter)
    val newLabel = stringResource(R.string.post_new)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        // A letter already read sits a shade darker, like paper that has been handled.
        color = if (letter.read) GardenColors.ParchmentDark else GardenColors.Parchment,
        border = BorderStroke(if (letter.read) 1.dp else 1.5.dp, if (letter.read) GardenColors.WoodLight else GardenColors.Wood),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = if (letter.read) title else "$newLabel $title" },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (letter.read) "📄" else "✉️", fontSize = 22.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = GardenColors.Ink,
                    fontWeight = if (letter.read) FontWeight.Normal else FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(sentDate(letter), color = GardenColors.InkSoft, style = MaterialTheme.typography.labelSmall)
            }
            if (!letter.read) NewStamp()
        }
    }
}

/** A small red stamp, set on a little askew, that says the letter is new. */
@Composable
private fun NewStamp() {
    Text(
        text = stringResource(R.string.post_new),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .rotate(-8f)
            .background(StampRed, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** One letter opened: its title, the whole of its words, and the date it came. */
@Composable
private fun LetterPage(letter: Letter) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = GardenColors.Parchment,
        border = BorderStroke(1.dp, GardenColors.WoodLight),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(letterTitle(letter), color = GardenColors.Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(letterBody(letter), color = GardenColors.Ink, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
            Text(
                text = sentDate(letter),
                color = GardenColors.InkSoft,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

private fun sentDate(letter: Letter): String = DATE.format(Instant.ofEpochMilli(letter.sentAt).atZone(ZoneId.systemDefault()))

@Composable
private fun letterTitle(letter: Letter): String {
    val name = stringResource(seasonLabel(letter.season))
    return when (letter.kind) {
        LetterKind.SEASON_ARRIVED -> stringResource(R.string.letter_arrived_title, seasonEmoji(letter.season), name)
        LetterKind.SEASON_COMING -> stringResource(R.string.letter_coming_title, seasonEmoji(letter.season), name)
        LetterKind.TIP -> letter.tip?.let { stringResource(tipText(it).first) } ?: stringResource(R.string.letter_tip_footer)
        LetterKind.NEW_STOCK -> stringResource(R.string.letter_stock_title)
        LetterKind.GARDEN_CAN_GROW -> stringResource(R.string.letter_grow_title)
    }
}

@Composable
private fun letterBody(letter: Letter): String {
    val name = stringResource(seasonLabel(letter.season))
    return when (letter.kind) {
        LetterKind.SEASON_ARRIVED -> listOfNotNull(
            stringResource(seasonScene(letter.season)),
            stringResource(R.string.letter_arrived_body, name),
            inSeasonLine(letter.season),
        ).joinToString("\n")
        LetterKind.SEASON_COMING -> stringResource(R.string.letter_coming_body, name)
        LetterKind.TIP -> listOfNotNull(
            letter.tip?.let { stringResource(tipText(it).second) },
            stringResource(R.string.letter_tip_footer),
        ).joinToString("\n\n")
        LetterKind.NEW_STOCK -> stockBody(letter)
        LetterKind.GARDEN_CAN_GROW -> stringResource(R.string.letter_grow_body, letter.growSize ?: 0)
    }
}

/** The crops whose season [season] is, by name; null when there are none. */
@Composable
private fun inSeasonLine(season: Season): String? {
    val crops = GardenItem.entries.filter { it.category == ItemCategory.SEED && season in it.seasons }
    if (crops.isEmpty()) return null
    return stringResource(R.string.letter_in_season, crops.map { "${itemEmoji(it)}${itemDisplayName(it)}" }.joinToString("、"))
}

/** What came into the shop, one per line, with each crop's season. */
@Composable
private fun stockBody(letter: Letter): String {
    val levels = letter.stockLevels ?: return stringResource(R.string.letter_stock_footer)
    val allYear = stringResource(R.string.letter_stock_all_year)
    val seasonNames = Season.entries.associateWith { stringResource(seasonLabel(it)) }
    val lines = GardenItem.unlockedBetween(levels.first, levels.last).map { item ->
        val line = "・${itemEmoji(item)} ${itemDisplayName(item)}"
        if (item.category != ItemCategory.SEED) {
            line
        } else {
            val seasons = if (item.seasons.isEmpty()) allYear else item.seasons.sorted().joinToString("・") { seasonNames.getValue(it) }
            line + "（" + stringResource(R.string.letter_stock_crop, seasons) + "）"
        }
    }
    return (listOf(stringResource(R.string.letter_stock_body, levels.last)) + lines + stringResource(R.string.letter_stock_footer))
        .joinToString("\n")
}

/** The title and words of each piece of advice. */
private fun tipText(tip: LetterTip): Pair<Int, Int> = when (tip) {
    LetterTip.WATERING -> R.string.letter_tip_watering_title to R.string.letter_tip_watering_body
    LetterTip.RAIN -> R.string.letter_tip_rain_title to R.string.letter_tip_rain_body
    LetterTip.COMPOST -> R.string.letter_tip_compost_title to R.string.letter_tip_compost_body
    LetterTip.STORE -> R.string.letter_tip_store_title to R.string.letter_tip_store_body
    LetterTip.CREATURES -> R.string.letter_tip_creatures_title to R.string.letter_tip_creatures_body
    LetterTip.GREENHOUSE -> R.string.letter_tip_greenhouse_title to R.string.letter_tip_greenhouse_body
}

/** A line about how the garden looks as [season] comes. */
@StringRes
private fun seasonScene(season: Season): Int = when (season) {
    Season.SPRING -> R.string.letter_scene_spring
    Season.SUMMER -> R.string.letter_scene_summer
    Season.AUTUMN -> R.string.letter_scene_autumn
    Season.WINTER -> R.string.letter_scene_winter
}
