package com.studioguu.miniaturegarden.ui

import com.studioguu.miniaturegarden.domain.model.BookUnlocks
import com.studioguu.miniaturegarden.domain.model.CollectionCategory
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.NatureNotes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1110 1-3: the level balloon says its sentence in full, the book counts all it holds, words stay whole. */
class WordingFb1110Test {

    private val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val strings = File(main, "res/values/strings.xml").readText()
    private fun string(name: String) = Regex("<string name=\"$name\">(.*?)</string>").find(strings)!!.groupValues[1]

    @Test
    fun `the level balloon ends its sentence however many things come`() {
        assertEquals("次の Lv で %1\$s が手に入ります", string("level_info_next_one"))
        assertEquals("次の Lv で %1\$s と %2\$s が手に入ります", string("level_info_next_two"))
        assertEquals("次の Lv で %1\$s や %2\$s などが手に入ります", string("level_info_next_many"))
        assertEquals("最大レベル", string("level_info_max"))
    }

    @Test
    fun `the book counts its pages and the creatures, 51 in all`() {
        assertEquals(20, CollectionEntry.inCategory(CollectionCategory.CROPS).size)
        assertEquals(7, CollectionEntry.inCategory(CollectionCategory.TERRAIN).size)
        assertEquals(18, CollectionEntry.inCategory(CollectionCategory.STRUCTURES).size)
        assertEquals(6, Creature.entries.size)
        assertEquals(51, BookUnlocks.TOTAL_KINDS)
        val progress = CollectionProgress(listOf(CollectionRecord(CollectionEntry.TULIP.objectId, 1, 0L, revealed = true)))
        assertEquals(1, BookUnlocks.kinds(progress, null))
        assertEquals(1, BookUnlocks.kinds(progress, NatureNotes(emptyList())))
        val cover = File(main, "java/com/studioguu/miniaturegarden/ui/collection/CollectionDialog.kt").readText()
        assertTrue(cover.contains("val total = BookUnlocks.TOTAL_KINDS"))
    }

    @Test
    fun `words the phrase breaker would split are kept whole`() {
        // Android's phrase line breaking cut "たい|肥" (device feedback 11/09); a word joiner holds them.
        for (word in listOf("たい肥", "ひと畝", "切り替え", "入れ替わり")) {
            assertFalse("$word is not glued", strings.contains(word))
        }
        assertTrue(string("hint_withered").contains("たい⁠肥"))
    }
}
