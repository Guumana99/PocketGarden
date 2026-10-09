package com.studioguu.miniaturegarden.data.security

import com.studioguu.miniaturegarden.data.security.SecurityManager.Companion.DRIFT_THRESHOLD_MILLIS
import com.studioguu.miniaturegarden.data.security.SecurityManager.Companion.isTimeTampered
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTamperDetectionTest {

    private val lastWall = 1_700_000_000_000L
    private val lastMono = 5_000_000L
    private val hour = 3_600_000L

    @Test
    fun `both clocks advancing together is clean`() {
        assertFalse(isTimeTampered(lastWall + hour, lastMono + hour, lastWall, lastMono, rebootedSinceLastSave = false))
    }

    @Test
    fun `wall clock behind the last save is tampering, reboot or not`() {
        assertTrue(isTimeTampered(lastWall - 1, lastMono + hour, lastWall, lastMono, rebootedSinceLastSave = false))
        assertTrue(isTimeTampered(lastWall - 1, 1_000L, lastWall, lastMono, rebootedSinceLastSave = true))
    }

    @Test
    fun `wall clock jumping ahead of the monotonic clock is tampering`() {
        val nowMono = lastMono + hour
        assertTrue(isTimeTampered(lastWall + hour + DRIFT_THRESHOLD_MILLIS, nowMono, lastWall, lastMono, false))
        assertFalse(isTimeTampered(lastWall + hour + DRIFT_THRESHOLD_MILLIS - 1, nowMono, lastWall, lastMono, false))
    }

    @Test
    fun `drift is not checked across a reboot`() {
        assertFalse(isTimeTampered(lastWall + 48 * hour, 30_000L, lastWall, lastMono, rebootedSinceLastSave = true))
    }
}
