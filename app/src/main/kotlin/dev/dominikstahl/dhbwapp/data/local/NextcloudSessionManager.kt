package dev.dominikstahl.dhbwapp.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class NextcloudSessionManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("Nextcloud", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALIAS = "nextcloudCredentials"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"

        private const val PREF_IV = "nextcloudIV"
        private const val PREF_CREDENTIALS = "nextcloudCredentials"
        private const val PREF_CONFIG = "nextcloudConfig"
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    data class NextcloudConfig(
        val serverUrl: String,
        val username: String,
        val appPassword: String
    )

    fun saveConfig(config: NextcloudConfig) {
        try {
            val key = getSecretKey()
            val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val serverBase64 = Base64.encodeToString(config.serverUrl.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val usernameBase64 = Base64.encodeToString(config.username.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val passwordBase64 = Base64.encodeToString(config.appPassword.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val payload = "$serverBase64:$usernameBase64:$passwordBase64"

            val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            sharedPreferences.edit()
                .putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(PREF_CREDENTIALS, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getConfig(): NextcloudConfig? {
        val ivBase64 = sharedPreferences.getString(PREF_IV, null) ?: return null
        val ciphertextBase64 = sharedPreferences.getString(PREF_CREDENTIALS, null) ?: return null

        try {
            val key = getSecretKey()
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            val payload = String(decryptedBytes, Charsets.UTF_8)
            val parts = payload.split(":")
            if (parts.size == 3) {
                val serverUrl = String(Base64.decode(parts[0], Base64.NO_WRAP), Charsets.UTF_8)
                val username = String(Base64.decode(parts[1], Base64.NO_WRAP), Charsets.UTF_8)
                val appPassword = String(Base64.decode(parts[2], Base64.NO_WRAP), Charsets.UTF_8)
                return NextcloudConfig(serverUrl, username, appPassword)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun clearConfig() {
        sharedPreferences.edit()
            .remove(PREF_IV)
            .remove(PREF_CREDENTIALS)
            .apply()
    }
}
