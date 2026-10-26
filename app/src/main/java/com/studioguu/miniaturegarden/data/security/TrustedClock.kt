package com.studioguu.miniaturegarden.data.security

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import com.studioguu.miniaturegarden.domain.repository.GameClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The game's clock ([GameClock]), kept honest without accusing anyone.
 *
 * While the device stays on, time is measured with elapsedRealtime, which the player cannot
 * set: the game's time is where it stood at an anchor plus how far that clock has run since.
 * After a restart that clock begins again from zero, so the anchor is set afresh: from the
 * network's time where the device has it (Android 13 and up; the app asks for no permission of
 * its own for this), or else from the device's clock, which then has to be believed (time away
 * is still counted for a week at most). Either way the game's time never goes back.
 *
 * The anchor is kept on disk, so a new process carries on where the last one left off.
 */
@Singleton
class TrustedClock @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : GameClock {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private var anchor: Anchor? = null

    @Synchronized
    override fun now(): Long {
        val saved = anchor ?: loadAnchor()
        val reading = Reading(SystemClock.elapsedRealtime(), bootCount())
        val (now, next) = resolve(saved, reading, ::outsideTime)
        anchor = next
        // Kept on disk when it is set afresh, and once a minute as it runs (see resolve), so
        // that after a restart the game's time is known never to go back past it.
        if (next != saved) saveAnchor(next)
        return now
    }

    /** The network's time if the device has it, else its own clock. */
    private fun outsideTime(): Long = networkTime() ?: System.currentTimeMillis()

    private fun networkTime(): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        // Throws when the device has not had the time from the network since it started.
        return runCatching { SystemClock.currentNetworkTimeClock().millis() }.getOrNull()
    }

    private fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, UNKNOWN_BOOT_COUNT)

    private fun loadAnchor(): Anchor? {
        if (!prefs.contains(KEY_TIME)) return null
        return Anchor(prefs.getLong(KEY_TIME, 0L), prefs.getLong(KEY_MONO, 0L), prefs.getInt(KEY_BOOT, UNKNOWN_BOOT_COUNT))
    }

    private fun saveAnchor(anchor: Anchor) = prefs.edit {
        putLong(KEY_TIME, anchor.time)
        putLong(KEY_MONO, anchor.mono)
        putInt(KEY_BOOT, anchor.boot)
    }

    /** The game's time [time] at the moment elapsedRealtime read [mono], in the boot numbered [boot]. */
    data class Anchor(val time: Long, val mono: Long, val boot: Int)

    /** elapsedRealtime and the boot count, read now. */
    data class Reading(val mono: Long, val boot: Int)

    companion object {
        private const val PREFS_NAME = "game_clock"
        private const val KEY_TIME = "anchor_time"
        private const val KEY_MONO = "anchor_mono"
        private const val KEY_BOOT = "anchor_boot"
        const val UNKNOWN_BOOT_COUNT = -1

        /** How often, at most, the running anchor is written down. */
        const val SAVE_EVERY_MILLIS = 60_000L

        /**
         * The game's time for [reading], from the [anchor] last set (null before the first time),
         * and the anchor to keep. Within one boot it is the anchor plus the elapsed time since.
         * Otherwise it starts from [outside] (the network's or the device's time), but never
         * before the anchor's time: a clock set back does not turn the garden back.
         */
        fun resolve(anchor: Anchor?, reading: Reading, outside: () -> Long): Pair<Long, Anchor> {
            if (anchor != null && anchor.boot == reading.boot && reading.mono >= anchor.mono) {
                val now = anchor.time + (reading.mono - anchor.mono)
                return now to if (reading.mono - anchor.mono >= SAVE_EVERY_MILLIS) Anchor(now, reading.mono, reading.boot) else anchor
            }
            val now = maxOf(outside(), anchor?.time ?: Long.MIN_VALUE)
            return now to Anchor(now, reading.mono, reading.boot)
        }
    }
}
