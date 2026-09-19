package com.pasargad.dezh.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.pasargad.dezh.cryptography.CryptoConstants
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Production [KeystoreGateway] backed by the hardware-backed "AndroidKeyStore".
 * The key is non-exportable (cannot be read out of the device) and every
 * encryption gets a Keystore-generated random IV (randomized encryption required).
 */
class AndroidKeystoreGateway(
    private val keyAlias: String,
) : KeystoreGateway {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        if (sealed.size <= IV_SIZE_BYTES) throw SecurityMetaCorruptedException()
        val iv = sealed.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = sealed.copyOfRange(IV_SIZE_BYTES, sealed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, iv))
        return try {
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            throw SecurityMetaCorruptedException()
        }
    }

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
    }
}

/** Meta file missing/undecryptable/tampered. No details are exposed. */
class SecurityMetaCorruptedException : IllegalStateException("Security metadata is missing or corrupted")
