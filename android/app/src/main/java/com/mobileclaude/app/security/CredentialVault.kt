package com.mobileclaude.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialVault(context: Context) {
    private val preferences = context.getSharedPreferences("credential_vault", Context.MODE_PRIVATE)
    private val alias = "claude_link_device_vault"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun savePrivateKey(profileId: String, privateKey: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(privateKey), Base64.NO_WRAP)
        preferences.edit().putString("key_$profileId", encoded).apply()
    }

    fun loadPrivateKey(profileId: String): ByteArray? {
        val encoded = preferences.getString("key_$profileId", null) ?: return null
        val parts = encoded.split(':', limit = 2)
        if (parts.size != 2) return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
        }.getOrNull()
    }

    fun delete(profileId: String) {
        preferences.edit().remove("key_$profileId").apply()
    }

    fun saveSecret(name: String, value: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value), Base64.NO_WRAP)
        preferences.edit().putString("secret_$name", encoded).apply()
    }

    fun loadSecret(name: String): ByteArray? {
        val encoded = preferences.getString("secret_$name", null) ?: return null
        val parts = encoded.split(':', limit = 2)
        if (parts.size != 2) return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
        }.getOrNull()
    }

    fun deleteSecret(name: String) {
        preferences.edit().remove("secret_$name").apply()
    }
}
