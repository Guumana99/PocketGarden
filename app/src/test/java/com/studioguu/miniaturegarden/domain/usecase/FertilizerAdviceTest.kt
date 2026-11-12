package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.FoodNote
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.LetterTip
import com.studioguu.miniaturegarden.domain.model.PostOffice
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.Soil
import com.studioguu.miniaturegarden.domain.model.SoilGrade
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1110 (10): the food bar's line, the tutorial and the Lv.3 letter say the same about fertilizer. */
class FertilizerAdviceTest {

    private val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val strings = File(main, "res/values/strings.xml").readText().replace("\u2060", "")
    private fun string(name: String) = Regex("<string name=\"$name\">(.*?)</string>").find(strings)!!.groupValues[1]

    @Test
    fun `the line under the food bar follows the food`() {
        val plain = SoilGrade.PLAIN
        assertEquals(FoodNote.FILLING, Soil.foodNote(0f, filling = true, grade = plain))
        assertEquals(FoodNote.EMPTY, Soil.foodNote(0f, filling = false, grade = plain))
        // A harvest's worth or less left: time to give some.
        assertEquals(FoodNote.LOW, Soil.foodNote(1f / 3f, filling = false, grade = plain))
        assertEquals(FoodNote.PLENTY, Soil.foodNote(0.67f, filling = false, grade = plain))
        assertEquals(FoodNote.PLENTY, Soil.foodNote(1f, filling = false, grade = plain))
        // Fluffy soil takes more a harvest, so "one harvest left" comes sooner.
        assertEquals(FoodNote.LOW, Soil.foodNote(0.5f, filling = false, grade = SoilGrade.FLUFFY))
    }

    @Test
    fun `the words of the food line`() {
        assertEquals("栄養があると、よい実りが出やすくなります", string("food_note_plenty"))
        assertEquals("そろそろ肥料のまきどきです", string("food_note_low"))
        assertEquals("肥料をまくと、また元気になります", string("food_note_empty"))
        val dialog = File(main, "java/com/studioguu/miniaturegarden/ui/hud/ObjectStatusDialog.kt").readText()
        assertTrue(dialog.contains("FoodNote.FILLING -> R.string.msg_feeding"))
    }

    @Test
    fun `the fertilizer letter comes once, at Lv 3, outside the turn of the others`() {
        val t0 = 1_000_000L
        assertNull(PostOffice.levelTip(2, emptyList(), Season.SPRING, t0))
        val letter = PostOffice.levelTip(3, emptyList(), Season.SPRING, t0)!!
        assertEquals(LetterTip.FERTILIZER, letter.tip)
        assertNull(PostOffice.levelTip(5, listOf(letter), Season.SPRING, t0 + 1))
        assertFalse(LetterTip.FERTILIZER in LetterTip.inTurn)
        assertEquals(Letter.tip(LetterTip.FERTILIZER, Season.SPRING, t0), letter)
    }

    @Test
    fun `tutorial, letter and panel tell the same things`() {
        val tutorial = string("tutorial_hint_feed") + string("tutorial_grow_text")
        val letter = string("letter_tip_fertilizer_body")
        for (said in listOf(tutorial, letter)) {
            assertTrue(said, said.contains("収穫\u00A03\u00A0回分"))
            assertTrue(said, said.contains("よい実り"))
            assertTrue(said, said.contains("土も"))
            assertTrue(said, said.contains("早さは変わらない"))
        }
        assertTrue(tutorial.contains("減ってきたら"))
        assertTrue(letter.contains("少なくなってきたら"))
        // The tutorial's fertilizer is still free.
        val screen = File(main, "java/com/studioguu/miniaturegarden/ui/GardenScreen.kt").readText()
        assertTrue(screen.contains("if (tutorialStep == TutorialStep.FEED_SEEDLING) 0 else Soil.fertilizerCost("))
    }
}
