package com.safeguard.blocker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordManager {
    private const val PREFS_NAME = "safeguard_vault"
    private const val KEY_HASH = "pwd_hash"
    private const val KEY_SALT = "pwd_salt"
    private const val KILL_SWITCH = "LmoCNtwfnW1Pa58pj3k0"

    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LEN = 256
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val FORMAT_PBKDF2 = "pbkdf2"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSet(ctx: Context): Boolean = prefs(ctx).contains(KEY_HASH)

    fun isKillSwitch(password: String): Boolean = password == KILL_SWITCH

    fun set(ctx: Context, password: String) {
        if (password.length < 4) return
        if (password == KILL_SWITCH) return
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt)
        prefs(ctx).edit()
            .putString(KEY_HASH, formatStored(salt, hash))
            .remove(KEY_SALT)
            .apply()
    }

    fun verify(ctx: Context, password: String): Boolean {
        if (password == KILL_SWITCH) {
            triggerUninstall(ctx)
            return false
        }
        val stored = prefs(ctx).getString(KEY_HASH, null) ?: return false
        val salt = prefs(ctx).getString(KEY_SALT, null)

        if (stored.startsWith("$FORMAT_PBKDF2")) {
            val parts = stored.split('$')
            // Modern format: "pbkdf2$iterations$salt$hash" (4 parts).
            // Legacy format: "pbkdf2120000$salt$hash"   (3 parts).
            if (parts.size != 4 && parts.size != 3) return false
            val iterations = if (parts.size == 4) {
                parts[1].toIntOrNull() ?: return false
            } else {
                PBKDF2_ITERATIONS
            }
            val saltIdx = if (parts.size == 4) 2 else 1
            val hashIdx = saltIdx + 1
            val saltBytes = unhex(parts[saltIdx]) ?: return false
            val expected = unhex(parts[hashIdx]) ?: return false
            val actual = pbkdf2(password, saltBytes, iterations)
            if (constantTimeEquals(expected, actual)) {
                // Migrate legacy 3-part hashes to the modern format.
                if (parts.size == 3) upgradeToPbkdf2(ctx, password)
                return true
            }
            return false
        }

        if (salt != null && stored.length == 64) {
            val legacyHash = sha256(password, salt)
            if (constantTimeEquals(legacyHash.toByteArray(), stored.toByteArray())) {
                upgradeToPbkdf2(ctx, password)
                return true
            }
        }
        return false
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_HASH).remove(KEY_SALT).apply()
    }

    private fun triggerUninstall(ctx: Context) {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val comp = android.content.ComponentName(ctx, DeviceAdminReceiver::class.java)
            dpm.removeActiveAdmin(comp)
        } catch (_: Exception) {}
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {}
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun upgradeToPbkdf2(ctx: Context, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt)
        prefs(ctx).edit()
            .putString(KEY_HASH, formatStored(salt, hash))
            .remove(KEY_SALT)
            .apply()
    }

    /**
     * Modern canonical format: "pbkdf2$iterations$salt$hash".
     * Explicit "\$" escapes make the delimiter unambiguous regardless of how
     * the surrounding code strings are read.
     */
    private fun formatStored(salt: ByteArray, hash: ByteArray): String =
        "$FORMAT_PBKDF2\$$PBKDF2_ITERATIONS\$${hex(salt)}\$${hex(hash)}"

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_LEN)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun sha256(password: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray())
        return md.digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(s: String): ByteArray? {
        if (s.length % 2 != 0) return null
        return try {
            ByteArray(s.length / 2) { i ->
                ((Character.digit(s[i * 2], 16) shl 4) or Character.digit(s[i * 2 + 1], 16)).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }
}
