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

class DualisCredentialsManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("Dualis", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALIAS = "dualisCredentials"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
        
        private const val PREF_IV = "dualisIV"
        private const val PREF_CREDENTIALS = "dualisCredentials"
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

    fun saveCredentials(email: String, password: String) {
        try {
            val key = getSecretKey()
            val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val emailBase64 = Base64.encodeToString(email.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val passwordBase64 = Base64.encodeToString(password.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val payload = "$emailBase64:$passwordBase64"

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

    fun getCredentials(): Pair<String, String>? {
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
            if (parts.size == 2) {
                val email = String(Base64.decode(parts[0], Base64.NO_WRAP), Charsets.UTF_8)
                val password = String(Base64.decode(parts[1], Base64.NO_WRAP), Charsets.UTF_8)
                return Pair(email, password)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun clearCredentials() {
        sharedPreferences.edit()
            .remove(PREF_IV)
            .remove(PREF_CREDENTIALS)
            .apply()
    }
}
