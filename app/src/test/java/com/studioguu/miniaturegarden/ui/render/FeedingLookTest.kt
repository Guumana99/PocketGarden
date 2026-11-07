package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.Soil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How fertilizer shows on its cell over time ([FeedingLook]). */
class FeedingLookTest {

    /** From empty: soaking in, then a hundred seconds of filling. */
    private val feeding = Soil.SOAK_MILLIS + 100_000L

    @Test
    fun `granules fall while it soaks in, then the soil glows and bubbles until the food is full, then a sparkle`() {
        val soaking = FeedingLook.at(1_000L, feeding)!!
        assertTrue(soaking.granules)
        assertFalse(soaking.bubbles)
        assertEquals(0f, soaking.glow, 0f)
        assertNull(soaking.sparkle)

        // However long the filling takes, it shows all the way.
        for (since in listOf(Soil.SOAK_MILLIS + 5_000L, 60_000L, feeding - 1L)) {
            val filling = FeedingLook.at(since, feeding)!!
            assertFalse(filling.granules)
            assertTrue(filling.bubbles)
            assertEquals(1f, filling.glow, 0f)
            assertEquals(1f, filling.gauge, 0f)
            assertNull(filling.sparkle)
        }

        val full = FeedingLook.at(feeding + 100L, feeding)!!
        assertFalse(full.bubbles)
        assertNotNull(full.sparkle)
        assertTrue(full.gauge < 1f)
    }

    @Test
    fun `nothing shows before the fertilizer is given, nor once the sparkle is over`() {
        assertNull(FeedingLook.at(-1L, feeding))
        assertNull(FeedingLook.at(feeding + FeedingLook.SPARKLE_MILLIS, feeding))
        assertNull(FeedingLook.at(feeding + 60_000L, feeding))
    }
}
