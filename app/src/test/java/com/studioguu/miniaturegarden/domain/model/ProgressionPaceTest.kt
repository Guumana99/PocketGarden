package com.studioguu.miniaturegarden.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How long the standard player takes to Lv.20 ([StandardPlayer.play]), with and without the ad
 * rewards. The figures are written to the test's output (system-out in its result XML) for
 * balancing; the asserts only keep them sensible, not to a number.
 */
class ProgressionPaceTest {

    @Test
    fun `the standard player reaches Lv_20 in months, a little sooner with the ads`() {
        val lines = mutableListOf<String>()
        // Beds that come with the garden's size, the garden grown as soon as it can be paid for.
        for (ads in listOf(false, true)) {
            val pace = StandardPlayer.play(20, ads, StandardPlayer.BedPlan.GROWING)
            val grew = pace.grewTo.joinToString(" ") { (size, day) -> "${size}x$size=%.1f".format(day) }
            val milestones = listOf(2, 5, 8, 10, 14, 15).joinToString(" ") { "Lv.$it=%.1f".format(pace.levels[it] ?: Double.NaN) }
            lines += "growing beds, ads=%-5s Lv.20 in %.1f days; garden grew: %s; %s".format(ads, pace.days, grew, milestones)
            assertTrue(pace.days in 20.0..1_000.0)
        }
        // For comparison: a fixed number of beds from the first day, as estimated before.
        for (beds in listOf(12, 20)) {
            val without = StandardPlayer.daysToLevel(20, beds, ads = false)
            val with = StandardPlayer.daysToLevel(20, beds, ads = true)
            lines += "fixed beds=%2d  Lv.20 without ads %.1f days, with ads %.1f days".format(beds, without, with)
            assertTrue(with <= without)
        }
        println(lines.joinToString("\n"))
    }
}
