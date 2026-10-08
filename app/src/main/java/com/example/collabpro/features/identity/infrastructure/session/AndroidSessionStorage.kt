package com.example.collabpro.features.identity.infrastructure.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class AndroidSessionStorage(context: Context) : EncryptedSessionStorage {
    private val preferences = context.getSharedPreferences("collabpro_session", Context.MODE_PRIVATE)
    override fun read(): ByteArray? = preferences.getString("encrypted", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
    override fun write(encrypted: ByteArray) = preferences.edit()
        .putString("encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()
    override fun clear() = preferences.edit().clear().commit()
}

internal class AndroidSessionKey {
    @Synchronized fun get(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    private companion object { const val ALIAS = "collabpro.session.aes.v1" }
}
