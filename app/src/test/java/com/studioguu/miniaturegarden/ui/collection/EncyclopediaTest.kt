package com.studioguu.miniaturegarden.ui.collection

import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every chapter of the book opens on a contents page and turns to an entry's page at a tap; an
 * entry's page carries the day it was entered as "yy/mm/dd", on a line apart from its words; and
 * the rumours are written without a leading "・".
 */
class EncyclopediaTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText().replace("\r\n", "\n")
    private val strings = File(main, "res/values/strings.xml").readText()
    private fun string(name: String) = Regex("<string name=\"$name\">(.*?)</string>").find(strings)?.groupValues?.get(1)

    @Test
    fun `the day an entry was made is written yy slash mm slash dd`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val millis = LocalDateTime.of(2026, 10, 19, 9, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals("26/10/19", bookDate(millis, zone))
        // Single figures keep their nought, and the year is two figures.
        val january = LocalDateTime.of(2027, 1, 5, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("27/01/05", bookDate(january, ZoneOffset.UTC))
        // The day is the one where the player is, not in UTC.
        val lateEvening = LocalDateTime.of(2026, 10, 19, 23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("26/10/20", bookDate(lateEvening, zone))
    }

    @Test
    fun `the date is labelled, and the old month-and-day wording is gone`() {
        assertEquals("登録日 %1\$s", string("book_entered_on"))
        assertEquals(null, string("nature_first_met"))
        // Both kinds of page put it in their header, above the words.
        val parts = source("ui/collection/BookParts.kt")
        val header = parts.substringAfter("internal fun PageHeader(").substringBefore("\n}\n")
        assertTrue(header.contains("R.string.book_entered_on, bookDate(enteredAt)"))
        assertTrue(source("ui/collection/CollectionDialog.kt").contains("PageHeader(entry.ordinal + 1, enteredAt"))
        assertTrue(source("ui/collection/NatureNotesChapter.kt").contains("PageHeader(creature.ordinal + 1, notes.noteOf(creature)?.firstSeenAt"))
    }

    @Test
    fun `rumours carry no leading dot`() {
        val notes = source("ui/collection/NatureNotesChapter.kt")
        assertFalse(notes.contains("\"・\""))
        assertTrue(notes.contains("BookText(stringResource(combinationHint(it)), faded = true)"))
        for (hint in Regex("<string name=\"hint_[a-z]+_[a-z_]+\">(.*?)</string>").findAll(strings)) {
            assertFalse(hint.value, hint.groupValues[1].startsWith("・"))
        }
    }

    @Test
    fun `every chapter opens on its contents and a tap turns to the entry`() {
        val book = source("ui/collection/CollectionDialog.kt")
        val chapter = book.substringAfter("private fun Chapter(").substringBefore("\n}\n")
        assertTrue(chapter.contains("rememberPagerState(initialPage = 0, pageCount = { entries.size + 1 })"))
        assertTrue(chapter.contains("ContentsPage(entries.size)"))
        assertTrue(chapter.contains("animateScrollToPage(index + 1)"))
        assertTrue(chapter.contains("BookFooter(pagerState, entries.size, onDismiss)"))

        val notes = source("ui/collection/NatureNotesChapter.kt")
        val nature = notes.substringAfter("internal fun NatureNotesChapter(").substringBefore("\n}\n")
        assertTrue(nature.contains("rememberPagerState(initialPage = 0, pageCount = { creatures.size + 1 })"))
        assertTrue(nature.contains("ContentsPage("))
        assertTrue(nature.contains("animateScrollToPage(index + 1)"))
        assertTrue(nature.contains("BookFooter(pagerState, creatures.size, onDismiss)"))
        // One contents layout for the whole book.
        assertFalse(notes.contains("CONTENTS_COLUMNS"))
        assertEquals(4, CONTENTS_COLUMNS)
    }

    @Test
    fun `the book is closed with the same wooden button as every other board`() {
        val footer = source("ui/collection/BookParts.kt").substringAfter("internal fun BookFooter(").substringBefore("\n}\n")
        assertTrue(footer.contains("WoodButton(text = stringResource(R.string.action_close), onClick = onDismiss)"))
        for (path in listOf("ui/collection/CollectionDialog.kt", "ui/collection/NatureNotesChapter.kt")) {
            assertFalse(path, source(path).contains("R.string.action_close"))
        }
    }
}
