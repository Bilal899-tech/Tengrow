package com.safeguard.blocker

import android.content.Context
import android.content.SharedPreferences

object PanicConfig {
    private const val PREFS = "panic_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WINDOW_MS = "window_ms"
    private const val KEY_TRIGGER_COUNT = "trigger_count"
    private const val KEY_LOCKDOWN_MS = "lockdown_ms"
    private const val KEY_CONTACT_NAME = "contact_name"
    private const val KEY_CONTACT_PHONE = "contact_phone"

    const val DEFAULT_WINDOW_MS = 5 * 60_000L
    const val DEFAULT_TRIGGER_COUNT = 3
    const val DEFAULT_LOCKDOWN_MS = 5 * 60_000L
    const val MIN_WINDOW_MS = 10_000L
    const val MAX_WINDOW_MS = 60 * 60_000L
    const val MIN_LOCKDOWN_MS = 60_000L
    const val MAX_LOCKDOWN_MS = 60 * 60_000L

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, true)

    fun windowMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_WINDOW_MS, DEFAULT_WINDOW_MS).coerceIn(MIN_WINDOW_MS, MAX_WINDOW_MS)

    fun triggerCount(ctx: Context): Int =
        prefs(ctx).getInt(KEY_TRIGGER_COUNT, DEFAULT_TRIGGER_COUNT).coerceIn(2, 20)

    fun lockdownMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_LOCKDOWN_MS, DEFAULT_LOCKDOWN_MS).coerceIn(MIN_LOCKDOWN_MS, MAX_LOCKDOWN_MS)

    fun contactName(ctx: Context): String = prefs(ctx).getString(KEY_CONTACT_NAME, "").orEmpty()

    fun contactPhone(ctx: Context): String = prefs(ctx).getString(KEY_CONTACT_PHONE, "").orEmpty()

    fun update(
        ctx: Context,
        enabled: Boolean,
        windowMs: Long,
        triggerCount: Int,
        lockdownMs: Long,
        contactName: String,
        contactPhone: String
    ) {
        prefs(ctx).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putLong(KEY_WINDOW_MS, windowMs.coerceIn(MIN_WINDOW_MS, MAX_WINDOW_MS))
            .putInt(KEY_TRIGGER_COUNT, triggerCount.coerceIn(2, 20))
            .putLong(KEY_LOCKDOWN_MS, lockdownMs.coerceIn(MIN_LOCKDOWN_MS, MAX_LOCKDOWN_MS))
            .putString(KEY_CONTACT_NAME, contactName.trim())
            .putString(KEY_CONTACT_PHONE, contactPhone.trim().filter { it.isDigit() })
            .apply()
    }

    fun summary(ctx: Context): String {
        if (!isEnabled(ctx)) return "Off"
        val minutes = windowMs(ctx) / 60_000L
        val cool = lockdownMs(ctx) / 60_000L
        return "${triggerCount(ctx)} hits in ${minutes}m • $cool min cool-down"
    }
}
