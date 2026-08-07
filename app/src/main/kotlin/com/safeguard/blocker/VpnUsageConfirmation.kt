package com.safeguard.blocker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of the background-usage confirmation (§3). */
sealed class VpnUsageVerification {
    /** The app was brought to the foreground within the window. */
    data class Confirmed(val packageNames: List<String>) : VpnUsageVerification()

    /** PACKAGE_USAGE_STATS not granted to us. */
    object PermissionDenied : VpnUsageVerification()

    /** No candidate was seen in the foreground recently. */
    object NoRecentUse : VpnUsageVerification()
}

/**
 * §3 — Background confirmation.
 *
 * Primary signal: [UsageStatsManager] — which events say a discovered VPN app
 * entered the foreground (UsageEvents.Event.ACTIVITY_RESUMED) inside the last
 * 5 minutes? Requires the PACKAGE_USAGE_STATS special access.
 *
 * Secondary (weak) signal: [ActivityManager.getRunningAppProcesses]. On
 * Android 10+ this only returns the caller's own processes, so it is
 * documented as unreliable and only used as a tie-breaker.
 */
object VpnUsageConfirmation {

    private const val WINDOW_MS = 5 * 60_000L

    /** True when the caller is permitted to read usage stats. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOp(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOp(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * Returns which candidates were foregrounded within the last 5 minutes.
     * Returns [VpnUsageVerification.PermissionDenied] when the special app-op
     * is missing — the caller should treat that as "cannot confirm".
     */
    suspend fun confirm(context: Context, candidates: List<String>): VpnUsageVerification =
        withContext(Dispatchers.IO) {
            if (!hasUsageAccess(context)) return@withContext VpnUsageVerification.PermissionDenied
            val um = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@withContext VpnUsageVerification.PermissionDenied

            val now = System.currentTimeMillis()
            val resumed = HashSet<String>()

            runCatching {
                val events = um.queryEvents(now - WINDOW_MS, now)
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        val p = event.packageName ?: continue
                        if (p in candidates && resumed.add(p) && resumed.size >= candidates.size) break
                    }
                }
            }.onFailure { return@withContext VpnUsageVerification.PermissionDenied }

            if (resumed.isEmpty()) return@withContext VpnUsageVerification.NoRecentUse
            return@withContext VpnUsageVerification.Confirmed(resumed.sorted())
        }

    /**
     * Weak secondary signal (§3): foreground processes via
     * ActivityManager. Unreliable on Android 10+ (only returns the caller's
     * own process), so this is used only as a tie-breaker next to usage stats.
     */
    fun runningForegroundProcesses(context: Context): List<String> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return emptyList()
        @Suppress("DEPRECATION")
        val running = runCatching { am.runningAppProcesses }.getOrDefault(null) ?: return emptyList()
        val result = ArrayList<String>()
        for (process in running) {
            if (process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                process.pkgList.forEach { result.add(it) }
            }
        }
        return result
    }
}