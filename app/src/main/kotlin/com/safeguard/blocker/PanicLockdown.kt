package com.safeguard.blocker

import android.content.Context

/**
 * Persistent cool-down (lockdown) timer.
 *
 * The end timestamp is written to SharedPreferences, so the countdown is
 * strictly accurate and survives process death, reboots and activity
 * recreation — an in-memory-only timer could be wiped by simply swiping the
 * app away, which defeats the accountability purpose.
 */
object PanicLockdown {

    private const val PREFS = "panic_lockdown"
    private const val KEY_END_MS = "end_ms"
    private const val KEY_DELETE_FAILS = "delete_fail_count"
    private const val KEY_DELETE_WINDOW_START = "delete_window_start_ms"

    private const val MAX_DELETE_ATTEMPTS = 3
    private const val DELETE_WINDOW_MS = 60_000L
    const val AUTO_LOCKDOWN_MS = 5 * 60_000L

    @Volatile private var endMs = 0L
    @Volatile private var loaded = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cache(ctx: Context): Long {
        if (!loaded) {
            endMs = prefs(ctx).getLong(KEY_END_MS, 0L)
            loaded = true
        }
        return endMs
    }

    @Synchronized
    fun start(ctx: Context, durationMs: Long = AUTO_LOCKDOWN_MS) {
        val end = System.currentTimeMillis() + durationMs
        endMs = end
        loaded = true
        prefs(ctx).edit().putLong(KEY_END_MS, end).apply()
    }

    @Synchronized
    fun isActive(ctx: Context): Boolean {
        val end = cache(ctx)
        if (end <= 0L) return false
        if (System.currentTimeMillis() >= end) {
            clear(ctx)
            return false
        }
        return true
    }

    @Synchronized
    fun remainingMs(ctx: Context): Long {
        if (!isActive(ctx)) return 0L
        return cache(ctx) - System.currentTimeMillis()
    }

    @Synchronized
    fun clear(ctx: Context) {
        endMs = 0L
        loaded = true
        prefs(ctx).edit().remove(KEY_END_MS).apply()
    }

    /**
     * Aggressive-deletion guard: counts failed master-password attempts for
     * protected actions (keyword deletion, uninstall protection bypasses).
     * Three failures inside a 60-second window auto-starts the strict 5-minute
     * cool-down timer and returns true.
     */
    @Synchronized
    fun registerFailedDeleteAttempt(ctx: Context): Boolean {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        val windowStart = p.getLong(KEY_DELETE_WINDOW_START, now)

        if (now - windowStart > DELETE_WINDOW_MS) {
            p.edit()
                .putLong(KEY_DELETE_WINDOW_START, now)
                .putInt(KEY_DELETE_FAILS, 1)
                .apply()
            return false
        }

        val fails = p.getInt(KEY_DELETE_FAILS, 0) + 1
        if (fails >= MAX_DELETE_ATTEMPTS) {
            p.edit()
                .remove(KEY_DELETE_FAILS)
                .remove(KEY_DELETE_WINDOW_START)
                .apply()
            start(ctx, AUTO_LOCKDOWN_MS)
            return true
        }
        // Persist the window start so the 60s window is enforced across
        // attempts made seconds or days apart.
        if (!p.contains(KEY_DELETE_WINDOW_START)) {
            val first = p.getLong(KEY_DELETE_WINDOW_START, now)
            p.edit()
                .putLong(KEY_DELETE_WINDOW_START, first)
                .putInt(KEY_DELETE_FAILS, fails)
                .apply()
        } else {
            p.edit().putInt(KEY_DELETE_FAILS, fails).apply()
        }
        return false
    }
}
