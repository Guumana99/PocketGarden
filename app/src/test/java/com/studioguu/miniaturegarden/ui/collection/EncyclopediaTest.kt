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
        assertTrue(chapter.contains("pagerState.turnTo(index + 1)"))
        assertTrue(chapter.contains("BookFooter(pagerState, entries.size, onDismiss)"))

        val notes = source("ui/collection/NatureNotesChapter.kt")
        val nature = notes.substringAfter("internal fun NatureNotesChapter(").substringBefore("\n}\n")
        assertTrue(nature.contains("rememberPagerState(initialPage = 0, pageCount = { creatures.size + 1 })"))
        assertTrue(nature.contains("ContentsPage("))
        assertTrue(nature.contains("pagerState.turnTo(index + 1)"))
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

    @Test
    fun `a page's words are broken by hand at their phrases, never left to wrap on the page`() {
        // A page holds about twelve characters to the line (the widest spread, at the usual font size).
        val strings = java.io.File("src/main/res/values/strings.xml").readText()
        val flavours = Regex("<string name=\"((?:creature_)?flavor_[a-z_]+)\">(.*?)</string>").findAll(strings).toList()
        assertTrue(flavours.size >= 50)
        for (flavour in flavours) {
            for (line in flavour.groupValues[2].split("\\n")) assertTrue("${flavour.groupValues[1]}: $line", line.length <= 12)
        }
    }

    @Test
    fun `a tulip's page is drawn in the colour first grown, with a place for every colour`() {
        val book = source("ui/collection/CollectionDialog.kt")
        // #59: the picture is the colour harvested, not one picked by the preview's own id.
        assertTrue(book.contains("EntryCell(entry, progress.stateOf(entry), progress.coloursOf(entry).firstOrNull(), atlas, terrain)"))
        assertTrue(book.contains("colours = progress.coloursOf(entry),"))
        assertTrue(book.contains("mutableStateOf(grownColours.firstOrNull())"))
        assertTrue(book.contains("Illustration(entry, state, reveal.value, shownColour, atlas, terrain, silhouette = notGrown)"))
        assertEquals(2, Regex("bloomColour = colour").findAll(book).count())
        assertTrue(source("ui/components/GardenCanvas.kt").contains("bloomColour = bloomColour,"))
        // The colour is chosen by the name; one not grown yet is a shadow and a hint, with no number in it.
        assertTrue(book.contains("BloomColourPicker(allColours, grownColours, shownColour) { shownColour = it }"))
        assertTrue(book.contains("flavorText(entry, shownColour)"))
        for (hint in listOf("bloom_hint_common", "bloom_hint_rare", "bloom_hint_very_rare")) {
            assertFalse(hint, string(hint)!!.any { it.isDigit() || it == '%' })
        }
        for (colour in com.studioguu.miniaturegarden.domain.model.PlantSpecies.TULIP.bloomColours) {
            assertTrue(colour, string("bloom_colour_$colour") != null)
            assertTrue(colour, string(if (colour == "red") "flavor_tulip" else "flavor_tulip_$colour") != null)
        }
    }

    @Test
    fun `outside the book a page's words run on, broken only after a sentence`() {
        // Device feedback 11/03: the shop's card broke the mint's words twelve characters a line.
        assertEquals(
            "季節を選ばない、丈夫な葉。\n触れると、すっと涼やかな香りが立つ。",
            asProse("季節を選ばない、\n丈夫な葉。\n触れると、すっと\n涼やかな香りが立つ。"),
        )
        assertEquals("一行だけ。", asProse("一行だけ。"))
        assertTrue(source("ui/shop/ShopStageScreen.kt").contains("Text(asProse(stringResource(flavorText(entry))), style"))
        // The book keeps its own breaks.
        assertFalse(source("ui/collection/CollectionDialog.kt").contains("asProse(stringResource"))
    }

    @Test
    fun `the book's picture of a thing shows the thing alone, in the middle`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("lawn = lawn, wholeBeds = true, river = river, portrait = true)"))
        // No blades at the foot of a fence, a lantern or a sprinkler, and no water thrown.
        assertTrue(canvas.contains("if (grassy && !portrait) drawGrassAtFoot("))
        assertTrue(canvas.contains("if (!portrait && !isGroundCover && structure == null && !obj.isPlant &&"))
        assertTrue(canvas.contains("if (obj.isSprinkler && obj.isOn && !obj.isWorking && !portrait) {"))
        // A building on its own patch: the well, one cell, no longer sits in the corner of two.
        assertTrue(canvas.contains("val size = maxOf(footprint.width, footprint.height)"))
        assertFalse(canvas.contains("maxOf(2, footprint.width, footprint.height)"))
    }

    @Test
    fun `the sprinkler's and the weathervane's pages say what they are for`() {
        assertEquals("くるくる回って、\\nまわりの畑に\\n水をまく。\\n止めておけば、\\nお庭の飾りにもなる。", string("flavor_sprinkler"))
        // The forecast opens from the weather icon at the top of the garden (onWeatherTap).
        val vane = string("flavor_weathervane")!!
        assertTrue(vane, vane.contains("天気アイコンを\\nタップすると"))
        assertTrue(vane, vane.contains("明日の天気が見られる。"))
        assertEquals("📖 図鑑に%1\$d種類登録すると、買えるようになります", string("shop_card_pages"))
    }
}
