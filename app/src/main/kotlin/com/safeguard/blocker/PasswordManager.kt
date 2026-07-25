package com.safeguard.blocker

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

object PasswordManager {
    private const val PREFS_NAME = "safeguard_vault"
    private const val KEY_HASH = "pwd_hash"
    private const val KEY_SALT = "pwd_salt"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSet(ctx: Context): Boolean = prefs(ctx).contains(KEY_HASH)

    fun set(ctx: Context, password: String) {
        val salt = generateSalt()
        val hash = hash(password, salt)
        prefs(ctx).edit()
            .putString(KEY_HASH, hash)
            .putString(KEY_SALT, salt)
            .apply()
    }

    fun verify(ctx: Context, password: String): Boolean {
        val hash = prefs(ctx).getString(KEY_HASH, null) ?: return false
        val salt = prefs(ctx).getString(KEY_SALT, null) ?: return false
        return hash(password, salt) == hash
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_HASH).remove(KEY_SALT).apply()
    }

    private fun hash(password: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray())
        return md.digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rng = SecureRandom()
        return (1..16).map { chars[rng.nextInt(chars.length)] }.joinToString("")
    }
}
