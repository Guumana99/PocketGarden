package com.studioguu.miniaturegarden.ui.collection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.NatureNotes
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.ui.combinationHint
import com.studioguu.miniaturegarden.ui.combinationLabel
import com.studioguu.miniaturegarden.ui.creatureFlavor
import com.studioguu.miniaturegarden.ui.creatureName
import com.studioguu.miniaturegarden.ui.render.STILL_CLOCK
import com.studioguu.miniaturegarden.ui.render.drawCreature
import com.studioguu.miniaturegarden.ui.render.drawCreatureShadow
import com.studioguu.miniaturegarden.ui.render.flies
import kotlinx.coroutines.launch

/**
 * The nature notes: one page per creature, written in as it is met. A creature met shows its
 * picture, a few words about it, and the combinations it has been seen coming for. For what is
 * not found yet there is only rumour: a quiet hint of what the creature seems to like, never the
 * exact recipe. Nothing here is counted or paid; it is a notebook, not a checklist.
 */
@Composable
internal fun NatureNotesChapter(notes: NatureNotes, onDismiss: () -> Unit) {
    val creatures = Creature.entries
    // Open at the last creature met, or at the first page.
    val pagerState = rememberPagerState(
        initialPage = creatures.indexOfLast(notes::hasMet).coerceAtLeast(0),
        pageCount = { creatures.size },
    )
    val scope = rememberCoroutineScope()

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(SPREAD_HEIGHT)) { page ->
        NaturePage(creatures[page], notes)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        PageButton("◀", enabled = pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
        Text(
            text = stringResource(R.string.collection_page, pagerState.currentPage + 1, creatures.size),
            color = GoldLeaf,
            fontFamily = BookFont,
            style = MaterialTheme.typography.titleSmall,
        )
        PageButton("▶", enabled = pagerState.currentPage < creatures.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.action_close), color = GoldLeaf, fontFamily = BookFont)
        }
    }
}

@Composable
private fun NaturePage(creature: Creature, notes: NatureNotes) {
    val met = notes.hasMet(creature)
    val rules = VisitRule.forCreature(creature)
    Row(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) {
        // Left page: the picture, or a question mark.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Paper, Paper, PaperShade)))
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PaperShade.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .border(2.dp, InkFaded, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (met) CreaturePicture(creature) else Text("？", color = InkFaded, fontSize = 64.sp, fontFamily = BookFont)
            }
        }
        Box(Modifier.width(2.dp).fillMaxHeight().background(InkFaded))
        // Right page: the words.
        Column(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(PaperShade, Paper, Paper)))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.collection_number, creature.ordinal + 1),
                color = InkFaded,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = if (met) stringResource(creatureName(creature)) else stringResource(R.string.nature_unknown),
                color = Ink,
                fontFamily = BookFont,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(InkFaded))
            if (met) {
                BookText(stringResource(creatureFlavor(creature)))
                val found = rules.filter(notes::hasFound)
                if (found.isNotEmpty()) {
                    BookHeading(stringResource(R.string.nature_heading_found))
                    found.forEach { BookText("✓ " + stringResource(combinationLabel(it))) }
                }
            }
            val rumours = rules.filterNot(notes::hasFound)
            if (rumours.isNotEmpty()) {
                BookHeading(stringResource(R.string.nature_heading_hints))
                rumours.forEach { BookText("・" + stringResource(combinationHint(it)), faded = true) }
            }
        }
    }
}

/** The creature as the garden draws it, standing still on a small patch of lawn. */
@Composable
private fun CreaturePicture(creature: Creature) {
    Canvas(Modifier.fillMaxSize()) {
        val unit = size.minDimension * 2.2f
        val ground = Offset(size.width / 2f, size.height * 0.72f)
        drawOval(LAWN, ground - Offset(unit * 0.2f, unit * 0.05f), Size(unit * 0.4f, unit * 0.1f))
        if (creature.flies()) {
            // It hovers well above its spot in the garden; here it is brought down into the frame,
            // with its shadow on the lawn below it.
            drawCreatureShadow(ground, unit, 0.12f)
            drawCreature(creature, ground + Offset(0f, unit * 0.25f), unit, STILL_CLOCK)
        } else {
            drawCreature(creature, ground, unit, STILL_CLOCK)
        }
    }
}

@Composable
private fun BookHeading(text: String) {
    Text(text, color = Ink, fontFamily = BookFont, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun BookText(text: String, faded: Boolean = false) {
    Text(
        text = text,
        color = if (faded) InkFaded else Ink,
        fontFamily = BookFont,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 2.dp),
    )
}

private val LAWN = Color(0xFFB9CF8C)
