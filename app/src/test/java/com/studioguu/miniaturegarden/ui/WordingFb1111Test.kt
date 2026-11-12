package com.studioguu.miniaturegarden.ui

import androidx.compose.ui.text.style.LineBreak
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.usecase.ActionResult
import com.studioguu.miniaturegarden.debug.TextAudit
import com.studioguu.miniaturegarden.ui.theme.BalloonLineBreak
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1111 1-2: a word in 「」 stays on one line, and a balloon's lines reach its edge. */
class WordingFb1111Test {

    private val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val strings = File(main, "res/values/strings.xml").readText()

    @Test
    fun `every word in brackets is held together, brackets and all`() {
        val quoted = Regex("「[^」<]*」").findAll(strings).map { it.value }.toList()
        assertTrue(quoted.size >= 7)
        for (word in quoted) {
            val letters = word.replace("⁠", "")
            assertEquals("$letters can break inside", letters.toList().joinToString("⁠"), word)
        }
    }

    @Test
    fun `the audit reads a line ending inside brackets as a cut word`() {
        val text = "収穫で「よい実り」が出ます"
        assertFalse(TextAudit.insideQuote(text, 2))
        assertTrue(TextAudit.insideQuote(text, 4))
        assertTrue(TextAudit.insideQuote(text, 8))
        assertFalse(TextAudit.insideQuote(text, 9))
    }

    @Test
    fun `the hoe on worked soil says nothing, and its other refusals speak of tilling`() {
        assertEquals(null, actionResultMessage(ActionResult.ALREADY_TILLED))
        assertEquals(R.string.msg_cannot_till, actionResultMessage(ActionResult.CANNOT_TILL))
        assertEquals(R.string.msg_no_room_to_plant, actionResultMessage(ActionResult.NO_ROOM_TO_PLANT))
        assertTrue(strings.contains("<string name=\"msg_cannot_till\">ここは耕せません</string>"))
        assertTrue(strings.contains("<string name=\"msg_no_room_to_plant\">他の物があるため植えられません</string>"))
    }

    @Test
    fun `balloons fill their lines, still breaking between phrases`() {
        assertEquals(LineBreak.Strategy.HighQuality, BalloonLineBreak.strategy)
        assertEquals(LineBreak.WordBreak.Phrase, BalloonLineBreak.wordBreak)
        assertEquals(LineBreak.Strictness.Strict, BalloonLineBreak.strictness)
        val steps = File(main, "java/com/studioguu/miniaturegarden/ui/onboarding/FirstSteps.kt").readText()
        assertEquals(2, Regex("""bodyMedium\.filledLines\(\)""").findAll(steps).count())
        val toast = File(main, "java/com/studioguu/miniaturegarden/ui/components/GardenToast.kt").readText()
        assertTrue(toast.contains("bodyMedium.filledLines()"))
    }
}
