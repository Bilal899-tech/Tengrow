package com.safeguard.blocker

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted local action log (spec §4: "log every action to an encrypted local
 * file (not Logcat)").
 *
 * AES/GCM encryption, ciphertext written to filesDir/vpn_actions.log. The key
 * lives in the Android Keystore on API 23+ so it never leaves the TEE; on
 * API 21/22 we fall back to a random key held in app-private prefs (best
 * effort — those devices predate hardware-backed key storage).
 */
object VpnActionLog {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "vpn_action_log_key"
    private const val PREFS = "vpn_log"
    private const val PREF_KEY = "fallback_key"

    @Volatile private var logFile: File? = null
    @Volatile private var key: SecretKey? = null

    fun record(context: Context, line: String) {
        if (line.isBlank()) return
        runCatching {
            val appKey = key ?: loadOrCreateKey(context).also { key = it }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, appKey)
            val encrypted = cipher.doFinal(line.toByteArray(Charsets.UTF_8))

            val iv = cipher.iv
            val entry = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP) + "\n"

            val file = logFile ?: File(context.filesDir, FILE_NAME).also { logFile = it }
            FileOutputStream(file, true).use { it.write(entry.toByteArray(Charsets.UTF_8)) }
            if (file.length() > MAX_LOG_BYTES) file.delete()
        }
    }

    private fun loadOrCreateKey(context: Context): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val stored = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (stored != null) return stored

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            kg.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            return kg.generateKey()
        }

        // API 21 / 22: no Keystore-backed AES. Store a random key in private
        // app storage (best effort below API 23).
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(PREF_KEY, null)
        if (existing != null) return SecretKeySpec(Base64.decode(existing, Base64.NO_WRAP), "AES")
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(PREF_KEY, Base64.encodeToString(raw, Base64.NO_WRAP)).apply()
        return SecretKeySpec(raw, "AES")
    }

    private const val MAX_LOG_BYTES = 512 * 1024L
    private const val FILE_NAME = "vpn_actions.log"
}