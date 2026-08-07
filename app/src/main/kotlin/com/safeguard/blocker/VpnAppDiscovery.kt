package com.safeguard.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * §1 — VPN App Discovery.
 *
 * Scans [PackageManager] for every app that exposes a BIND_VPN_SERVICE
 * vpn service (i.e. can legally act as a VPN tunnel). Results are cached and
 * only re-scanned when package state changes (new installs / uninstalls are
 * pushed in by [VpnPackageReceiver], or the cache naturally expires).
 *
 * The heavy query runs on the IO dispatcher so it never stalls the UI.
 */
object VpnAppDiscovery {

    private const val CACHE_TTL_MS = 60_000L

    private val cache = ConcurrentHashMap<String, VpnApp>()
    @Volatile private var cachedAt = 0L

    /** Apps holding BIND_VPN_SERVICE, cached. */
    suspend fun discover(context: Context): List<VpnApp> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cache.isNotEmpty() && now - cachedAt < CACHE_TTL_MS) {
            return@withContext cache.values.sortedBy { it.packageName }
        }
        val found = scan(context)
        cache.clear()
        found.forEach { cache[it.packageName] = it }
                cachedAt = now
        found
    }

    /** Drop the cache (e.g. after ACTION_PACKAGE_ADDED/REMOVED). */
    fun invalidate() {
        cache.clear()
        cachedAt = 0L
    }

    /** Synchronous read of whatever is cached — never queries PackageManager. */
    fun peek(): List<VpnApp> = cache.values.sortedBy { it.packageName }

    /** Immediate (blocking) refresh used off the main thread. */
    fun refreshSync(context: Context) {
        val found = scan(context)
        cache.clear()
        found.forEach { cache[it.packageName] = it }
        cachedAt = System.currentTimeMillis()
    }

    private fun scan(context: Context): List<VpnApp> {
        val pm = context.packageManager
        // Query services advertising the VpnService action. The system
        // grants BIND_VPN_SERVICE only to apps that declare a service with
        // this intent — exactly our target population.
        val matches = try {
            pm.queryIntentServices(
                Intent(VpnService.SERVICE_INTERFACE),
                PackageManager.MATCH_ALL
            )
        } catch (e: Exception) {
            emptyList()
        }
        val apps = ArrayList<VpnApp>(matches.size)
        val seen = HashSet<String>()
        for (resolveInfo in matches) {
            val sinfo = resolveInfo.serviceInfo ?: continue
            val pkg = sinfo.packageName
            if (!seen.add(pkg)) continue
            val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            val label = appInfo?.loadLabel(pm)?.toString() ?: pkg
            val isSystem = (appInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0
            apps += VpnApp(pkg, label, isSystem)
        }
        return apps.sortedBy { it.packageName }
    }
}