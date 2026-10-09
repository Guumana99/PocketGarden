package com.studioguu.minituregarden.data.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import androidx.core.content.edit
import com.studioguu.minituregarden.data.local.entity.UserProfileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clock-tamper detection, profile signing and the database passphrase.
 *
 * Two clocks are recorded on every profile save: the wall clock (user-adjustable) and
 * elapsedRealtime (monotonic, resets on reboot). Comparing how far each advanced since the last
 * save exposes clock changes made while the device stayed up.
 */
@Singleton
class SecurityManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    fun currentTimeMillis(): Long = System.currentTimeMillis()

    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    /**
     * Compares the current clocks with those stored at the last save and returns true if the
     * wall clock was moved. A positive result also raises the sticky tamper flag.
     */
    fun detectTimeTampering(lastUpdatedTimestamp: Long, lastMonoClockTime: Long): Boolean {
        val nowMono = elapsedRealtime()
        val tampered = isTimeTampered(
            nowWall = currentTimeMillis(),
            nowMono = nowMono,
            lastWall = lastUpdatedTimestamp,
            lastMono = lastMonoClockTime,
            rebootedSinceLastSave = hasRebootedSinceLastSave(nowMono, lastMonoClockTime),
        )
        if (tampered) setTamperFlag(true)
        return tampered
    }

    fun isTamperFlagged(): Boolean = prefs.getBoolean(KEY_TAMPER_FLAG, false)

    fun setTamperFlag(flagged: Boolean) = prefs.edit { putBoolean(KEY_TAMPER_FLAG, flagged) }

    /**
     * Returns [profile] with both clocks set to now and a fresh signature. Also remembers the
     * current boot count so the next check knows whether the monotonic clock is still comparable.
     */
    fun stampAndSign(profile: UserProfileEntity): UserProfileEntity {
        val stamped = profile.copy(
            lastUpdatedTimestamp = currentTimeMillis(),
            lastMonoClockTime = elapsedRealtime(),
        )
        prefs.edit { putInt(KEY_BOOT_COUNT, currentBootCount()) }
        return stamped.copy(signature = CryptoUtils.sign(signaturePayload(stamped)))
    }

    fun verifySignature(profile: UserProfileEntity): Boolean =
        CryptoUtils.verify(signaturePayload(profile), profile.signature)

    /**
     * SQLCipher passphrase: 32 random bytes, generated once and stored wrapped by a Keystore key.
     * Throws if the Keystore key is gone (the existing database is then undecryptable anyway).
     */
    @Synchronized
    fun getDatabasePassphrase(): ByteArray {
        prefs.getString(KEY_DB_PASSPHRASE, null)?.let {
            return CryptoUtils.decrypt(Base64.decode(it, Base64.NO_WRAP))
        }
        val passphrase = ByteArray(DB_PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = Base64.encodeToString(CryptoUtils.encrypt(passphrase), Base64.NO_WRAP)
        // commit(): the passphrase must be on disk before a database is created with it.
        prefs.edit(commit = true) { putString(KEY_DB_PASSPHRASE, wrapped) }
        return passphrase
    }

    private fun hasRebootedSinceLastSave(nowMono: Long, lastMono: Long): Boolean {
        val current = currentBootCount()
        val saved = prefs.getInt(KEY_BOOT_COUNT, UNKNOWN_BOOT_COUNT)
        // When the boot count is unavailable, assume a reboot: that skips the drift check
        // instead of risking a false accusation.
        return current == UNKNOWN_BOOT_COUNT || saved == UNKNOWN_BOOT_COUNT || current != saved || nowMono < lastMono
    }

    private fun currentBootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, UNKNOWN_BOOT_COUNT)

    private fun signaturePayload(profile: UserProfileEntity): String = listOf(
        SIGNATURE_VERSION,
        profile.id,
        profile.coins,
        profile.playerLevel,
        profile.inventoryCapacity,
        profile.lastUpdatedTimestamp,
        profile.lastMonoClockTime,
    ).joinToString("|")

    companion object {
        /** Wall clock running this far ahead of the monotonic clock counts as a speed hack. */
        const val DRIFT_THRESHOLD_MILLIS = 5L * 60 * 1000

        private const val PREFS_NAME = "security_state"
        private const val KEY_TAMPER_FLAG = "tamper_flag"
        private const val KEY_BOOT_COUNT = "boot_count"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val UNKNOWN_BOOT_COUNT = -1
        private const val DB_PASSPHRASE_BYTES = 32
        private const val SIGNATURE_VERSION = "v1"

        /**
         * 1. Wall clock earlier than the last save -> rolled back.
         * 2. Without a reboot in between, both clocks must have advanced by the same amount;
         *    a wall clock that gained [DRIFT_THRESHOLD_MILLIS] or more on top was fast-forwarded.
         *    After a reboot the monotonic clock restarted, so only check 1 applies.
         */
        fun isTimeTampered(
            nowWall: Long,
            nowMono: Long,
            lastWall: Long,
            lastMono: Long,
            rebootedSinceLastSave: Boolean,
        ): Boolean {
            if (nowWall < lastWall) return true
            if (rebootedSinceLastSave) return false
            val wallElapsed = nowWall - lastWall
            val monoElapsed = nowMono - lastMono
            return wallElapsed - monoElapsed >= DRIFT_THRESHOLD_MILLIS
        }
    }
}
