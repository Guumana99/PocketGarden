package com.studioguu.miniaturegarden.domain.model

import kotlin.random.Random
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1110 (6): a visitor's next move is one of several kinds, never the same kind twice running. */
class CreatureMoodTest {

    @Test
    fun `every kind comes up, none twice in a row, roughly by its weight`() {
        val random = Random(7)
        var last: Mood? = null
        val counts = HashMap<Mood, Int>()
        repeat(4000) {
            val mood = Mood.next(random, last)
            assertNotEquals(last, mood)
            counts[mood] = (counts[mood] ?: 0) + 1
            last = mood
        }
        assertTrue(counts.keys == Mood.entries.toSet())
        // The usual move is still the commonest; staying put the rarest.
        assertTrue(counts.getValue(Mood.USUAL) > counts.getValue(Mood.STILL))
    }

    @Test
    fun `a shuffle is short, a wander long, and staying put stops longest`() {
        val random = Random(3)
        repeat(200) {
            assertTrue(Mood.SHUFFLE.strideScale(random) < Mood.WANDER.strideScale(random))
            assertTrue(Mood.SHUFFLE.restScale(random) < Mood.STILL.restScale(random))
        }
    }
}
