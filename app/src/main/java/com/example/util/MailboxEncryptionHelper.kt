package com.example.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object MailboxEncryptionHelper {

    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    private fun deriveKey(npsn: String, pairingKey: String): SecretKeySpec {
        val seed = "${npsn.trim()}_${pairingKey.trim()}_BKU_SECURE_SALT"
        val md = MessageDigest.getInstance("SHA-256")
        val keyBytes = md.digest(seed.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Generates a deterministic, unique, and secure Mailbox Room / Channel ID
     */
    fun deriveMailboxChannelId(npsn: String, pairingKey: String): String {
        val seed = "BKU_CHANNEL_${npsn.trim()}_${pairingKey.trim()}"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(seed.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.take(32)
    }

    /**
     * Encrypts plaintext string using AES-256-CBC.
     * Returns Pair(encryptedBase64, ivBase64).
     */
    fun encrypt(plaintext: String, npsn: String, pairingKey: String): Pair<String, String> {
        val secretKey = deriveKey(npsn, pairingKey)
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        return Pair(encryptedBase64, ivBase64)
    }

    /**
     * Decrypts ciphertextBase64 using AES-256-CBC and ivBase64.
     */
    fun decrypt(encryptedBase64: String, ivBase64: String, npsn: String, pairingKey: String): String {
        val secretKey = deriveKey(npsn, pairingKey)
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
