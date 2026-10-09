package com.studioguu.miniaturegarden.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Thin wrappers over Android Keystore keys. The key material never leaves the Keystore, so
 * neither the HMAC key nor the wrapping key can be read out of the app's data directory.
 */
object CryptoUtils {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val HMAC_KEY_ALIAS = "pocketgarden_hmac_key"
    private const val AES_KEY_ALIAS = "pocketgarden_wrap_key"
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(KEYSTORE).apply { load(null) } }

    /** Base64 HMAC-SHA256 of [payload]. */
    fun sign(payload: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply { init(hmacKey()) }
        return Base64.encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    /** Constant-time comparison of [signature] against a freshly computed one. */
    fun verify(payload: String, signature: String): Boolean {
        if (signature.isEmpty()) return false
        return MessageDigest.isEqual(
            sign(payload).toByteArray(Charsets.UTF_8),
            signature.toByteArray(Charsets.UTF_8),
        )
    }

    /** AES-256-GCM; the random IV is prepended to the ciphertext. */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, aesKey()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(encrypted: ByteArray): ByteArray {
        val spec = GCMParameterSpec(GCM_TAG_BITS, encrypted, 0, GCM_IV_BYTES)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, aesKey(), spec) }
        return cipher.doFinal(encrypted, GCM_IV_BYTES, encrypted.size - GCM_IV_BYTES)
    }

    @Synchronized
    private fun hmacKey(): SecretKey =
        existingKey(HMAC_KEY_ALIAS) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    HMAC_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                ).build()
            )
            generateKey()
        }

    @Synchronized
    private fun aesKey(): SecretKey =
        existingKey(AES_KEY_ALIAS) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    AES_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }

    private fun existingKey(alias: String): SecretKey? = keyStore.getKey(alias, null) as? SecretKey
}
