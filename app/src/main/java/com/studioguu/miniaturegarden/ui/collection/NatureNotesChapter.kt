package com.studioguu.miniaturegarden.ui.collection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.NatureNotes
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.ui.combinationHint
import com.studioguu.miniaturegarden.ui.combinationLabel
import com.studioguu.miniaturegarden.ui.creatureFlavor
import com.studioguu.miniaturegarden.ui.creatureName
import com.studioguu.miniaturegarden.ui.render.STILL_CLOCK
import com.studioguu.miniaturegarden.ui.render.drawCreatureShadow
import com.studioguu.miniaturegarden.ui.render.drawVisitor
import com.studioguu.miniaturegarden.ui.render.flies
import kotlinx.coroutines.launch

/**
 * The nature notes: a contents page, then one page per creature, written in as it is met. The
 * contents show every creature at a glance: the ones met in colour, the rest as a faint shadow.
 * A creature met shows its picture, a few words about it, the day it first came and the
 * combinations it has been seen coming for. For what is not found yet there is only rumour: a
 * quiet hint of what the creature seems to like, never the exact recipe. Nothing here is counted
 * or paid; it is a notebook, not a checklist.
 *
 * A creature newly met is marked "new" until its page is turned to ([onOpenNote]); the mark stays
 * for as long as the book is open, so it does not vanish under the player's eyes.
 */
@Composable
internal fun NatureNotesChapter(notes: NatureNotes, atlas: SpriteAtlas, onOpenNote: (Creature) -> Unit, onDismiss: () -> Unit) {
    val creatures = Creature.entries
    val newOnes = remember { creatures.filter(notes::isNew).toSet() }
    // Page 0 is the contents; creature i is on page i + 1. Like every chapter, the book opens at
    // the contents, where a creature newly met is marked.
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { creatures.size + 1 })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) {
        val creature = creatures.getOrNull(pagerState.currentPage - 1) ?: return@LaunchedEffect
        if (notes.isNew(creature)) onOpenNote(creature)
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(SPREAD_HEIGHT)) { page ->
        if (page == 0) {
            ContentsPage(
                count = creatures.size,
                header = if (notes.met == 0) ({ NoneYet() }) else null,
            ) { index ->
                val creature = creatures[index]
                val met = notes.hasMet(creature)
                ContentsCell(
                    label = if (met) stringResource(creatureName(creature)) else stringResource(R.string.nature_unknown),
                    known = met,
                    onClick = { scope.launch { pagerState.turnTo(index + 1) } },
                    mark = if (creature in newOnes) ({ NewMark() }) else null,
                ) {
                    CreaturePicture(creature, atlas, silhouette = !met)
                }
            }
        } else {
            val creature = creatures[page - 1]
            NaturePage(creature, notes, isNew = creature in newOnes, atlas = atlas)
        }
    }

    BookFooter(pagerState, creatures.size, onDismiss)
}

/** Said on the contents while no creature has come yet. */
@Composable
private fun NoneYet() {
    Text(
        text = stringResource(R.string.nature_none_yet),
        color = InkFaded,
        fontFamily = BookFont,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The small red "new" label on a creature newly met. */
@Composable
private fun NewMark(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.nature_new),
        color = Color.White,
        fontFamily = BookFont,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        modifier = modifier
            .background(ReadyMark, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun NaturePage(creature: Creature, notes: NatureNotes, isNew: Boolean, atlas: SpriteAtlas) {
    val met = notes.hasMet(creature)
    val rules = VisitRule.forCreature(creature)
    Row(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) {
        // Left page: the picture, or the creature's faint shadow.
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
                CreaturePicture(creature, atlas, silhouette = !met)
                if (isNew) NewMark(Modifier.align(Alignment.TopEnd).padding(6.dp))
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
            PageHeader(creature.ordinal + 1, notes.noteOf(creature)?.firstSeenAt.takeIf { met })
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
                rumours.forEach { BookText(stringResource(combinationHint(it)), faded = true) }
            }
        }
    }
}

/**
 * The creature as the garden draws it, standing still on a small patch of lawn. Not met yet, it
 * is only a faint shadow of its shape, with no lawn under it.
 */
@Composable
private fun CreaturePicture(creature: Creature, atlas: SpriteAtlas, silhouette: Boolean = false) {
    // The shadow is the picture with its colours replaced: drawn on a layer of its own, then filled.
    val layer = if (silhouette) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier
    val atlasImage = remember(atlas) { atlas.bitmap?.asImageBitmap() }
    Canvas(Modifier.fillMaxSize().then(layer)) {
        val unit = size.minDimension * 2.2f
        val ground = Offset(size.width / 2f, size.height * 0.72f)
        if (!silhouette) drawOval(LAWN, ground - Offset(unit * 0.2f, unit * 0.05f), Size(unit * 0.4f, unit * 0.1f))
        if (creature.flies()) {
            // It hovers well above its spot in the garden; here it is brought down into the frame,
            // with its shadow on the lawn below it.
            drawCreatureShadow(ground, unit, 0.12f)
            drawVisitor(creature, ground + Offset(0f, unit * 0.25f), unit, STILL_CLOCK, atlas, atlasImage, shadow = false)
        } else {
            drawVisitor(creature, ground, unit, STILL_CLOCK, atlas, atlasImage)
        }
        if (silhouette) drawRect(SHADOW, blendMode = BlendMode.SrcIn)
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
private val SHADOW = Color(0x668A6F55)
