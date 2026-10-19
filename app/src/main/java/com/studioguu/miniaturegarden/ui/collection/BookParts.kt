package com.studioguu.miniaturegarden.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.withTick
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/*
 * The parts every chapter of the book is made of, so that each one reads the same way: a contents
 * page first, every entry at a glance, and a tap on one turns to its page. The page names the
 * entry, with its number and the day it was entered on a line of their own above the words.
 */

/** Cells to a row on a contents page. */
internal const val CONTENTS_COLUMNS = 4
private val CONTENTS_ROW_HEIGHT = 84.dp

private val BOOK_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yy/MM/dd")

/** The day [millis] fell on, as the book writes it: "26/10/19". */
internal fun bookDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(BOOK_DATE)

/**
 * A contents page: [count] cells, [CONTENTS_COLUMNS] to a row, scrolled when they do not all fit.
 * [cell] draws the entry at an index.
 */
@Composable
internal fun ContentsPage(count: Int, header: (@Composable () -> Unit)? = null, cell: @Composable (index: Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        header?.invoke()
        (0 until count).chunked(CONTENTS_COLUMNS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(CONTENTS_ROW_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { index -> Box(Modifier.weight(1f).fillMaxSize()) { cell(index) } }
                repeat(CONTENTS_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One cell of a contents page: a small picture over the entry's name, in a frame. [mark] is laid
 * in its top corner (a page waiting, a creature newly met); [highlighted] edges it in gold.
 */
@Composable
internal fun ContentsCell(
    label: String,
    known: Boolean,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    mark: (@Composable () -> Unit)? = null,
    picture: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(PaperShade.copy(alpha = 0.6f))
            .border(if (highlighted) 2.dp else 1.dp, if (highlighted) GoldLeaf else InkFaded, RoundedCornerShape(4.dp))
            .clickable(onClick = withTick(onClick))
            .padding(4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { picture() }
            Text(
                text = label,
                color = if (known) Ink else InkFaded,
                fontFamily = BookFont,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        if (mark != null) Box(Modifier.align(Alignment.TopEnd)) { mark() }
    }
}

/** The small red dot on a contents cell whose page is waiting to be filled in. */
@Composable
internal fun ReadyDot() {
    Box(Modifier.padding(2.dp).size(8.dp).background(ReadyMark, CircleShape))
}

/**
 * The top of an entry's page: its number, and on the same line, apart from the words below, the
 * day it was entered in the book ([enteredAt], none while the page is still blank).
 */
@Composable
internal fun PageHeader(number: Int, enteredAt: Long?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.collection_number, number),
            color = InkFaded,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.weight(1f))
        if (enteredAt != null) {
            Text(
                text = stringResource(R.string.book_entered_on, bookDate(enteredAt)),
                color = InkFaded,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .border(1.dp, InkFaded.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * Under the pages: back and forward, where the book is open ("もくじ" on the contents, "n / N"
 * on an entry's page), and the button that closes the book. From an entry's page a button goes
 * straight back to the contents. Page 0 is the contents; entry i is on page i + 1.
 */
@Composable
internal fun BookFooter(pagerState: PagerState, entryCount: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically) {
        PageButton("◀", enabled = pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
        Text(
            text = if (pagerState.currentPage == 0) {
                stringResource(R.string.nature_index)
            } else {
                stringResource(R.string.collection_page, pagerState.currentPage, entryCount)
            },
            color = GoldLeaf,
            fontFamily = BookFont,
            style = MaterialTheme.typography.titleSmall,
            modifier = if (pagerState.currentPage == 0) {
                Modifier
            } else {
                Modifier.clickable { scope.launch { pagerState.animateScrollToPage(0) } }
            },
        )
        PageButton("▶", enabled = pagerState.currentPage < entryCount) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
        Spacer(Modifier.weight(1f))
        if (pagerState.currentPage > 0) {
            WoodButton(
                text = stringResource(R.string.book_to_contents),
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        WoodButton(text = stringResource(R.string.action_close), onClick = onDismiss)
    }
}
