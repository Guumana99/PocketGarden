package com.studioguu.miniaturegarden.ui

import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.ui.components.GardenToastState
import com.studioguu.miniaturegarden.ui.components.toastMillis
import com.studioguu.miniaturegarden.ui.effects.GardenEffect
import com.studioguu.miniaturegarden.ui.effects.effectTiming
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One way of writing each word, descriptions that match the game, and messages left up long enough to read (#52). */
class WordingAndToastTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val strings = File(main, "res/values/strings.xml").readText()
    private fun string(name: String) = Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(strings)?.groupValues?.get(1)

    /** Every string's text, without the comments around them. */
    private val texts: List<String> = Regex("<string name=\"[^\"]+\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        .findAll(strings).map { it.groupValues[1] }.toList()

    private fun sourcesContaining(word: String): List<String> =
        File(main, "java").walkTopDown().filter { it.isFile && it.extension == "kt" && it.readText().contains(word) }.map { it.name }.toList()

    @Test
    fun `the book is 図鑑 everywhere, never ずかん`() {
        assertTrue(texts.none { it.contains("ずかん") })
        assertEquals(emptyList<String>(), sourcesContaining("ずかん"))
        assertEquals("ガーデン図鑑", string("collection_title"))
        assertTrue(texts.count { it.contains("図鑑") } >= 10)
    }

    @Test
    fun `each word is written one way`() {
        // Unlocking is 解放, putting away is 片づけ, the bag is 持ち物, a crop is 作物, coins are 🪙.
        for (stray in listOf("開放", "片付け", "かばん", "草地", "植物ではありません", "植物をタップ")) {
            assertTrue(stray, texts.none { it.contains(stray) })
        }
        assertTrue(texts.none { Regex("%\\d\\\$dG").containsMatchIn(it) })
        assertEquals("🎒 持ち物に戻しました", string("msg_stored"))
        assertEquals("🔒 Lv.%1\$d で解放", string("shop_locked"))
        assertTrue(string("level_up_unlocked")!!.contains("解放"))
    }

    @Test
    fun `the cottage's page says how the garden really grows`() {
        val cottage = string("flavor_cottage")!!
        // Growing is the player's choice from the finished cottage, not something smoke does.
        assertFalse(cottage.contains("煙"))
        assertTrue(cottage.contains("完成"))
        assertTrue(cottage.contains("広げられる"))
    }

    @Test
    fun `harvest sums and new pages stay up long enough to read`() {
        val harvest = effectTiming(GardenEffect.Harvest(1, coins = 10, xp = 5))
        val discovery = effectTiming(GardenEffect.Discovery(2))
        val built = effectTiming(GardenEffect.WorkDone(3, "struct_hut", WorkKind.BUILDING))
        // About two seconds fully readable for a harvest, more for a new page, most for a building.
        assertTrue(harvest.durationMillis * harvest.visibleShare >= 1_800f)
        assertTrue(discovery.durationMillis * discovery.visibleShare >= 2_400f)
        assertTrue(harvest.durationMillis < discovery.durationMillis)
        assertTrue(discovery.durationMillis <= built.durationMillis)
    }

    @Test
    fun `the book writes nothing over itself when a page is filled in or the day's coins come`() {
        assertEquals(null, string("collection_revealed_coins"))
        assertEquals(null, string("collection_daily_reward"))
        val book = File(main, "java/com/studioguu/miniaturegarden/ui/collection/CollectionDialog.kt").readText()
        assertFalse(book.contains("NoticeStrip"))
        val model = File(main, "java/com/studioguu/miniaturegarden/ui/collection/CollectionViewModel.kt").readText()
        assertFalse(model.contains("BookNotice"))
        // The coins still chime.
        assertTrue(model.contains("audio.play(SoundEffect.COIN)"))
    }
}
