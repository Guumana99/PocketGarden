package com.studioguu.miniaturegarden.ui.hud

import org.junit.Assert.assertEquals
import org.junit.Test

/** How the time left on a building is put into words, now that buildings take hours. */
class SpokenDurationTest {

    @Test
    fun `under an hour it is minutes and seconds`() {
        assertEquals(SpokenDuration(0, 0, 45), SpokenDuration.of(45))
        assertEquals(SpokenDuration(0, 2, 0), SpokenDuration.of(120))
        assertEquals(SpokenDuration(0, 59, 59), SpokenDuration.of(3_599))
    }

    @Test
    fun `from an hour on it is hours and minutes, never reading shorter than it is`() {
        assertEquals(SpokenDuration(1, 0, 0), SpokenDuration.of(3_600))
        // A second past the hour already reads as the next minute.
        assertEquals(SpokenDuration(1, 1, 0), SpokenDuration.of(3_601))
        assertEquals(SpokenDuration(1, 45, 0), SpokenDuration.of(6_300))
        // Just under two hours left still says two hours.
        assertEquals(SpokenDuration(2, 0, 0), SpokenDuration.of(7_170))
        assertEquals(SpokenDuration(2, 0, 0), SpokenDuration.of(7_200))
    }

    @Test
    fun `nothing below zero`() {
        assertEquals(SpokenDuration(0, 0, 0), SpokenDuration.of(-5))
    }
}
