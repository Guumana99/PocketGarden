package com.studioguu.miniaturegarden.data.security

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.core.content.edit
import com.studioguu.miniaturegarden.data.local.entity.UserProfileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profile signing, the tamper flag and the database passphrase.
 *
 * The device's clock is not checked here any more: the game keeps its own time ([TrustedClock]),
 * which a clock set forward or back does not move, so there is nothing to catch or hold against
 * the player. The two clocks are still stamped on every profile save, as part of what is signed.
 */
@Singleton
class SecurityManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    fun currentTimeMillis(): Long = System.currentTimeMillis()

    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    fun isTamperFlagged(): Boolean = prefs.getBoolean(KEY_TAMPER_FLAG, false)

    fun setTamperFlag(flagged: Boolean) = prefs.edit { putBoolean(KEY_TAMPER_FLAG, flagged) }

    /** Returns [profile] with both clocks set to now and a fresh signature. */
    fun stampAndSign(profile: UserProfileEntity): UserProfileEntity {
        val stamped = profile.copy(
            lastUpdatedTimestamp = currentTimeMillis(),
            lastMonoClockTime = elapsedRealtime(),
        )
        return stamped.copy(signature = CryptoUtils.sign(signaturePayload(stamped)))
    }

    fun verifySignature(profile: UserProfileEntity): Boolean =
        CryptoUtils.verify(signaturePayload(profile), profile.signature) ||
            // A profile migrated from schema v1 still carries its old signature until the next
            // save; it can only have the 0 XP the migration gave it.
            (profile.currentXp == 0L && CryptoUtils.verify(legacySignaturePayload(profile), profile.signature))

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

    private fun signaturePayload(profile: UserProfileEntity): String = listOf(
        SIGNATURE_VERSION,
        profile.id,
        profile.coins,
        profile.playerLevel,
        profile.currentXp,
        profile.inventoryCapacity,
        profile.lastUpdatedTimestamp,
        profile.lastMonoClockTime,
    ).joinToString("|")

    /** Payload as signed before schema v2, i.e. without the experience points. */
    private fun legacySignaturePayload(profile: UserProfileEntity): String = listOf(
        LEGACY_SIGNATURE_VERSION,
        profile.id,
        profile.coins,
        profile.playerLevel,
        profile.inventoryCapacity,
        profile.lastUpdatedTimestamp,
        profile.lastMonoClockTime,
    ).joinToString("|")

    companion object {
        private const val PREFS_NAME = "security_state"

        /**
         * Raised by a broken signature only. Before the game kept its own time, a clock set
         * forward or back raised the old flag ("tamper_flag") for good and no building could be
         * finished after that; that flag is left behind, so every garden starts with a clean slate.
         */
        private const val KEY_TAMPER_FLAG = "tamper_flag_v2"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val DB_PASSPHRASE_BYTES = 32
        private const val SIGNATURE_VERSION = "v2"
        private const val LEGACY_SIGNATURE_VERSION = "v1"
    }
}
