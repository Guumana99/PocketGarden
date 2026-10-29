package com.studioguu.miniaturegarden.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How long the standard player takes to Lv.20 ([StandardPlayer.daysToLevel]), with and without
 * the ad rewards. The figures are written to the test's output (system-out in its result XML) for
 * balancing; the asserts only keep them sensible, not to a number.
 */
class ProgressionPaceTest {

    @Test
    fun `the standard player reaches Lv_20 in months, a little sooner with the ads`() {
        val lines = mutableListOf<String>()
        for (beds in listOf(8, 12, 16, 20)) {
            val without = StandardPlayer.daysToLevel(20, beds, ads = false)
            val with = StandardPlayer.daysToLevel(20, beds, ads = true)
            val milestones = listOf(2, 5, 8, 10, 15).joinToString(" ") { "Lv.$it=%.1f".format(StandardPlayer.daysToLevel(it, beds, ads = false)) }
            lines += "beds=%2d  Lv.20 without ads %.1f days, with ads %.1f days   (without: %s)".format(beds, without, with, milestones)
            assertTrue(with <= without)
            assertTrue(without in 20.0..1_000.0)
        }
        println(lines.joinToString("\n"))
    }
}
