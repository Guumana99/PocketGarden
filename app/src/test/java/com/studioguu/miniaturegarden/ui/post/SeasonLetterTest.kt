package com.studioguu.miniaturegarden.ui.post

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The letter that comes with a new season says plainly which crops can be grown now. */
class SeasonLetterTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val strings = File(main, "res/values/strings.xml").readText()
    private fun string(name: String) = Regex("<string name=\"$name\">(.*?)</string>").find(strings)?.groupValues?.get(1)

    @Test
    fun `the crops are run together the way a letter would`() {
        assertEquals("", cropList(emptyList()))
        assertEquals("トマト", cropList(listOf("トマト")))
        assertEquals("トマトやスイカ", cropList(listOf("トマト", "スイカ")))
        assertEquals("トマトやスイカ、ひまわりなど", cropList(listOf("トマト", "スイカ", "ひまわり")))
    }

    @Test
    fun `the letter names the crops, not their seedlings`() {
        val body = string("letter_arrived_body")!!
        assertEquals("%1\$sは、%2\$sが育てられる季節です。ショップで種を手に入れて、さっそく育ててみませんか。", body)
        assertFalse(body.contains("苗"))
        assertEquals(null, string("letter_in_season"))
        // The names are the crops' own ("トマト"), not the shop's ("トマトの苗").
        val dialog = File(main, "java/com/studioguu/miniaturegarden/ui/post/PostDialog.kt").readText().replace("\r\n", "\n")
        val line = dialog.substringAfter("private fun inSeasonLine(").substringBefore("\n}\n")
        assertTrue(line.contains("cropList(crops.map { objectDisplayName(it.objectId) })"))
        assertFalse(line.contains("itemDisplayName"))
        assertFalse(string("letter_arrived_body_none")!!.contains("苗"))
    }
}
