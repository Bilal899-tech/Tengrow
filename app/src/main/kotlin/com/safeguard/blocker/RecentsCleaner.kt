package com.safeguard.blocker

import android.content.Context
import android.content.SharedPreferences

/**
 * Short-lived queue of packages that were just blocked.
 *
 * The block engine (LightweightBlockerService) records the package of every
 * app that triggered a block here, together with a timestamp. When the user
 * later opens the Recents/Overview screen, [RecentsSwiper] reads this queue
 * and tries to dismiss the matching task cards.
 *
 * Entries expire after [ENTRY_TTL_MS] — a block older than that is not worth
 * swiping away, the user is unlikely to still land on that card.
 */
object RecentsCleaner {

    private const val PREFS = "blocked_packages_for_recents"
    private const val KEY_ENTRIES = "blocked_entries"

    /** Entries older than this are dropped on every read/write. */
    private const val ENTRY_TTL_MS = 30_000L

    /** Remember a freshly blocked app so its recents card can be removed. */
    @Synchronized
    fun recordBlock(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        val now = System.currentTimeMillis()
        val prefs = prefs(context)
        purgeLocked(prefs, now)
        val entries = (prefs.getStringSet(KEY_ENTRIES, null) ?: emptySet()).toMutableSet()
        // One entry per package — a re-block refreshes the timestamp.
        entries.removeAll { it.startsWith("$packageName=") }
        entries.add("$packageName=$now")
        prefs.edit().putStringSet(KEY_ENTRIES, entries).apply()
    }

    /** Package names that are still inside the 30s window, newest first. */
    @Synchronized
    fun pendingPackages(context: Context): List<String> {
        val now = System.currentTimeMillis()
        val prefs = prefs(context)
        val entries = prefs.getStringSet(KEY_ENTRIES, null) ?: emptySet()
        val alive = ArrayList<Pair<String, Long>>(entries.size)
        val expired = ArrayList<String>()
        for (raw in entries) {
            val eq = raw.indexOf('=')
            if (eq <= 0) {
                expired.add(raw)
                continue
            }
            val ts = raw.substring(eq + 1).toLongOrNull()
            if (ts == null) {
                expired.add(raw)
                continue
            }
            if (now - ts > ENTRY_TTL_MS) expired.add(raw) else alive.add(raw.substring(0, eq) to ts)
        }
        if (expired.isNotEmpty()) {
            prefs.edit().putStringSet(KEY_ENTRIES, entries - expired.toSet()).apply()
        }
        return alive.sortedByDescending { it.second }.map { it.first }
    }

    /** Forget a package (successful swipe or permanent failure). */
    @Synchronized
    fun clear(context: Context, packageName: String) {
        val prefs = prefs(context)
        val entries = (prefs.getStringSet(KEY_ENTRIES, null) ?: emptySet()).toMutableSet()
        entries.removeAll { it.startsWith("$packageName=") }
        prefs.edit().putStringSet(KEY_ENTRIES, entries).apply()
    }

    private fun purgeLocked(prefs: SharedPreferences, now: Long) {
        val entries = prefs.getStringSet(KEY_ENTRIES, null) ?: return
        val expired = entries.filter {
            val eq = it.indexOf('=')
            if (eq <= 0) return@filter true
            val ts = it.substring(eq + 1).toLongOrNull() ?: return@filter true
            now - ts > ENTRY_TTL_MS
        }
        if (expired.isNotEmpty()) {
            prefs.edit().putStringSet(KEY_ENTRIES, entries - expired.toSet()).apply()
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
