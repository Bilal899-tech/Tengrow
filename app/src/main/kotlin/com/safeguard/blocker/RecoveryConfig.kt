package com.safeguard.blocker

import android.content.Context
import java.security.MessageDigest

object RecoveryConfig {

    private val BACKUP_CODE_HASH = hashRaw("lP4VJI2mqS7qwPP2IZnL")
    private const val CONTACT_EMAIL = "tengrow@nexagaze.com"
    private const val PREFS = "safeguard_vault"
    private const val KEY_RECOVERY_GRANTED = "recovery_granted_until_ms"
    private const val KEY_FAIL_COUNT = "recovery_fail_count"
    private const val KEY_FAIL_RESET = "recovery_fail_reset_at"
    private const val MAX_ATTEMPTS = 3
    private const val LOCKOUT_MS = 60_000L

    fun instruction(): String =
        "If you forgot your password, please contact $CONTACT_EMAIL from your registered email address to request a one-time recovery code. Once you receive the code, enter it below."

    fun contactEmail(): String = CONTACT_EMAIL

    fun checkBackupCode(input: String): Boolean {
        val clean = input.trim()
        if (clean.length < 12) return false
        return hashRaw(clean) == BACKUP_CODE_HASH
    }

    fun isRateLimited(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val resetAt = prefs.getLong(KEY_FAIL_RESET, 0L)
        if (System.currentTimeMillis() < resetAt) return true
        return false
    }

    fun recordAttempt(ctx: Context, success: Boolean) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (success) {
            prefs.edit()
                .remove(KEY_FAIL_COUNT)
                .remove(KEY_FAIL_RESET)
                .apply()
            return
        }
        val count = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
        if (count >= MAX_ATTEMPTS) {
            val until = System.currentTimeMillis() + LOCKOUT_MS
            prefs.edit()
                .putInt(KEY_FAIL_COUNT, count)
                .putLong(KEY_FAIL_RESET, until)
                .apply()
        } else {
            prefs.edit()
                .putInt(KEY_FAIL_COUNT, count)
                .apply()
        }
    }

    fun markRecoveryGranted(ctx: Context) {
        val until = System.currentTimeMillis() + 90_000L
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_RECOVERY_GRANTED, until)
            .apply()
    }

    fun isRecoveryGranted(ctx: Context): Boolean {
        val until = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_RECOVERY_GRANTED, 0L)
        return System.currentTimeMillis() < until
    }

    fun consumeRecoveryGranted(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_RECOVERY_GRANTED)
            .apply()
    }

    private fun hashRaw(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
