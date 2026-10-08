package com.example.collabpro.features.identity.infrastructure.session

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Ciphertext is versioned. A fresh random IV is generated for every save. */
internal class SessionCipher(private val key: () -> SecretKey) {
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return byteArrayOf(1, cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size >= 30 && encrypted[0] == 1.toByte())
        val ivLength = encrypted[1].toInt()
        require(ivLength == 12 && encrypted.size > 2 + ivLength)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(2, 2 + ivLength)))
        return cipher.doFinal(encrypted.copyOfRange(2 + ivLength, encrypted.size))
    }
}

internal interface EncryptedSessionStorage {
    fun read(): ByteArray?
    fun write(encrypted: ByteArray): Boolean
    fun clear(): Boolean
}
