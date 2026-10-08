package com.example.collabpro.features.campaign.infrastructure.drafts

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class AndroidCampaignDraftStorage(context: Context) : CampaignDraftStorage {
    private val preferences = context.getSharedPreferences("collabpro_campaign_drafts", Context.MODE_PRIVATE)
    override fun read(owner: UUID) = preferences.getString(owner.toString(), null)?.let { Base64.decode(it, Base64.NO_WRAP) }
    override fun write(owner: UUID, bytes: ByteArray) = preferences.edit().putString(owner.toString(), Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
    override fun clear(owner: UUID) = preferences.edit().remove(owner.toString()).commit()
}
internal class AndroidCampaignDraftKey {
    @Synchronized fun get(): SecretKey {
        val alias = "collabpro.campaignDraft.aes.v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
}
