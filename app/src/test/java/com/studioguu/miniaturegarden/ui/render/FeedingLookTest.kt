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

    @Test
    fun `granules fall while it soaks in, then the soil glows and bubbles while the food fills, then a sparkle`() {
        val soaking = FeedingLook.at(1_000L)!!
        assertTrue(soaking.granules)
        assertFalse(soaking.bubbles)
        assertEquals(0f, soaking.glow, 0f)
        assertNull(soaking.sparkle)

        val filling = FeedingLook.at(Soil.SOAK_MILLIS + 5_000L)!!
        assertFalse(filling.granules)
        assertTrue(filling.bubbles)
        assertEquals(1f, filling.glow, 0f)
        assertEquals(1f, filling.gauge, 0f)
        assertNull(filling.sparkle)

        val full = FeedingLook.at(Soil.FEEDING_MILLIS + 100L)!!
        assertFalse(full.bubbles)
        assertNotNull(full.sparkle)
        assertTrue(full.gauge < 1f)
    }

    @Test
    fun `nothing shows before the fertilizer is given, nor once the sparkle is over`() {
        assertNull(FeedingLook.at(-1L))
        assertNull(FeedingLook.at(FeedingLook.SHOW_MILLIS))
        assertNull(FeedingLook.at(Soil.FEEDING_MILLIS + 60_000L))
    }
}
