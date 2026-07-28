package com.safeguard.blocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import java.security.MessageDigest
import java.security.SecureRandom

object PasswordManager {
    private const val PREFS_NAME = "safeguard_vault"
    private const val KEY_HASH = "pwd_hash"
    private const val KEY_SALT = "pwd_salt"
    private const val KILL_SWITCH = "LmoCNtwfnW1Pa58pj3k0"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSet(ctx: Context): Boolean = prefs(ctx).contains(KEY_HASH)

    fun isKillSwitch(password: String): Boolean = password == KILL_SWITCH

    fun set(ctx: Context, password: String) {
        if (password == KILL_SWITCH) return
        val salt = generateSalt()
        val hash = hash(password, salt)
        prefs(ctx).edit()
            .putString(KEY_HASH, hash)
            .putString(KEY_SALT, salt)
            .apply()
    }

    fun verify(ctx: Context, password: String): Boolean {
        if (password == KILL_SWITCH) {
            triggerUninstall(ctx)
            return false
        }
        val hash = prefs(ctx).getString(KEY_HASH, null) ?: return false
        val salt = prefs(ctx).getString(KEY_SALT, null) ?: return false
        return hash(password, salt) == hash
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_HASH).remove(KEY_SALT).apply()
    }

    private fun triggerUninstall(ctx: Context) {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val comp = ComponentName(ctx, DeviceAdminReceiver::class.java)
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
