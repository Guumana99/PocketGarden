package com.studioguu.miniaturegarden.ui

import com.studioguu.miniaturegarden.data.ads.AdAvailability
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.NatureNotes
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.ui.debug.Showcase
import com.studioguu.miniaturegarden.ui.debug.adsOr
import com.studioguu.miniaturegarden.ui.post.LetterFit
import com.studioguu.miniaturegarden.ui.shop.BONUS_NOTICE_MILLIS
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Device feedback of 2026-11-02, where a screen cannot be run: read off the sources, or worked out. */
class FieldFeedbackTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText().replace("\r\n", "\n")
    private fun string(name: String): String? {
        val strings = File(main, "res/values/strings.xml").readText()
        return Regex("<string name=\"$name\">(.*?)</string>").find(strings)?.groupValues?.get(1)
    }

    @Test
    fun `the next ad on its way changes nothing, and only failing for a while makes it unavailable`() {
        assertEquals(AdAvailability.READY, AdAvailability.of(loaded = true, failuresInARow = 0))
        assertEquals(AdAvailability.LOADING, AdAvailability.of(loaded = false, failuresInARow = 0))
        assertEquals(AdAvailability.LOADING, AdAvailability.of(loaded = false, failuresInARow = AdAvailability.FAILURES_BEFORE_UNAVAILABLE - 1))
        assertEquals(AdAvailability.UNAVAILABLE, AdAvailability.of(loaded = false, failuresInARow = AdAvailability.FAILURES_BEFORE_UNAVAILABLE))
        assertEquals(AdAvailability.READY, AdAvailability.of(loaded = true, failuresInARow = 99))
    }

    @Test
    fun `the bonus board follows only the ads that have stopped coming`() {
        val screen = source("ui/shop/ShopStageScreen.kt")
        assertTrue(screen.contains("bonusRemaining > 0 && ads != AdAvailability.UNAVAILABLE && activity != null"))
        assertFalse(string("msg_reward_loading")!!.contains("準備中"))
    }

    @Test
    fun `a showcase still says how the ads stand`() {
        assertEquals(AdAvailability.LOADING, (null as Showcase?).adsOr(AdAvailability.LOADING))
        assertEquals(AdAvailability.READY, Showcase(adReady = true).adsOr(AdAvailability.LOADING))
        assertEquals(AdAvailability.UNAVAILABLE, Showcase(adReady = false).adsOr(AdAvailability.READY))
    }

    @Test
    fun `how a bonus went stays long enough to be read, then goes by itself`() {
        assertEquals(4_000L, BONUS_NOTICE_MILLIS)
        val model = source("ui/shop/ShopViewModel.kt")
        val claim = model.substringAfter("fun claimBonus(").substringBefore("fun buy(")
        assertFalse(claim.contains("_notice.value = ShopNotice"))
        assertTrue(claim.contains("_notice.compareAndSet(notice, null)"))
    }

    @Test
    fun `the banks of a stream are kept off the beds and the paving beside it`() {
        assertTrue(source("ui/render/RiverScene.kt").contains("it.clipOutPath(keptOut)"))
        assertTrue(source("ui/components/GardenCanvas.kt").contains("river.drawBanks(this, transform, floors)"))
    }

    @Test
    fun `the buttons down the edge go cloud, book, pencil, camera, post, settings, and the tools last`() {
        val screen = source("ui/GardenScreen.kt")
        val order = listOf("RainCloud(", "SideButton(\"📖\"", "SideButton(\"✏️\"", "SideButton(\"📷\"", "SideButton(\"📮\"", "SideButton(\"⚙️\"", "SideButton(\"🛠\"")
        val places = order.map { screen.indexOf(it) }
        assertTrue(places.all { it >= 0 })
        assertEquals(places.sorted(), places)
    }

    @Test
    fun `the tools are for a debug build only`() {
        val screen = source("ui/GardenScreen.kt")
        assertTrue(screen.substringBefore("SideButton(\"🛠\"").endsWith("if (BuildConfig.DEBUG) {\n                    "))
        assertTrue(source("ui/GardenViewModel.kt").substringAfter("fun debugCallCreatures(").contains("if (!BuildConfig.DEBUG) return"))
    }

    @Test
    fun `the first steps' last words are broken where they were asked to be`() {
        assertEquals("ここから先は、あなただけのお庭づくり。\\nお庭に「はじめてのおくりもの」\\nをおいたよ。開けてみてね。", string("tutorial_done_text"))
    }

    @Test
    fun `the whole book shown is every page and colour, registered today, with nothing marked new`() {
        val today = 1_700_000_000_000L
        val book = CollectionProgress.allRegistered(today)
        assertEquals(book.total, book.revealed)
        assertEquals(0, book.ready)
        for (entry in CollectionEntry.entries) assertEquals(today, book.recordOf(entry)?.firstObtainedAt)
        assertEquals(PlantSpecies.ROSE.bloomColours.toSet(), book.coloursOf(CollectionEntry.ROSE).toSet())
        val notes = NatureNotes.allMet(today)
        assertEquals(Creature.entries.size, notes.met)
        assertEquals(0, notes.unopened)
        assertTrue(VisitRule.entries.all(notes::hasFound))
    }

    @Test
    fun `showing the whole book changes the pages only, and writes nothing`() {
        val book = source("ui/collection/CollectionDialog.kt")
        // The cover, the bookmarks' dots and where the book opens go by the saved records.
        assertTrue(book.contains("Cover(loaded)"))
        assertTrue(book.contains("progress = loaded,\n                    natureNew"))
        assertTrue(book.contains("progress = pages,"))
        val preview = source("ui/debug/Showcase.kt").substringAfter("object BookPreview").substringBefore("/** Where the showcase")
        assertTrue(preview.contains("if (BuildConfig.DEBUG)"))
        assertFalse(preview.contains("Repository"))
        assertFalse(source("ui/GardenViewModel.kt").contains("BookPreview"))
    }

    @Test
    fun `a letter goes down a little to keep its lines, and no further`() {
        assertEquals(1f, LetterFit.of(200f, 300f), 0f)
        assertEquals(0.9f, LetterFit.of(300f, 270f), 1e-4f)
        assertEquals(LetterFit.SMALLEST, LetterFit.of(1_000f, 270f), 0f)
    }
}
