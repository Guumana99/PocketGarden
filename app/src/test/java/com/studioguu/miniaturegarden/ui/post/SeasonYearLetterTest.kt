package com.studioguu.miniaturegarden.ui.post

import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.SeasonClock
import com.studioguu.miniaturegarden.domain.model.SeasonYears
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1110 (11): from the second year a season's letter says which year it is, in words of that year. */
class SeasonYearLetterTest {

    private val t0 = 1_700_000_000_000L
    private val season = SeasonClock.SEASON_MILLIS

    @Test
    fun `a year turns at spring, counted from the season the garden started in`() {
        // Started in summer: summer, autumn, winter are year 1; the next spring is year 2.
        val letters = (0 until 9).map { i -> Letter.arrived(Season.entries[(1 + i) % 4], t0 + i * season) }
        val years = SeasonYears.of(letters)
        assertEquals(listOf(1, 1, 1, 2, 2, 2, 2, 3, 3), letters.map { years.getValue(it.letterId) })
        // Seasons gone by while away are skipped, and the count still comes out right.
        val gap = listOf(Letter.arrived(Season.SPRING, t0), Letter.arrived(Season.WINTER, t0 + 7 * season))
        assertEquals(listOf(1, 2), gap.map { SeasonYears.of(gap).getValue(it.letterId) })
    }

    @Test
    fun `four seasons by four years of words, the fifth carrying its own number`() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val strings = File(main, "res/values/strings.xml").readText()
        fun string(name: String) = Regex("<string name=\"$name\">(.*?)</string>").find(strings)!!.groupValues[1]
        val all = mutableSetOf<String>()
        for (year in 2..5) for (s in listOf("spring", "summer", "autumn", "winter")) {
            val text = string("letter_year${year}_$s")
            all += text
            if (year == 5) assertTrue(text, text.startsWith("%1\$d\u00A0年目の"))
        }
        assertEquals("no two years say the same", 16, all.size)
        assertEquals("%1\$s %2\$d\u00A0年目の%3\$sになりました", string("letter_arrived_title_year"))
        assertEquals(5, SeasonYears.LAST_WRITTEN_YEAR)
    }
}
