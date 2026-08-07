package com.safeguard.blocker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Outcome of an active-VPN probe (§2). */
sealed class VpnActiveStatus {
    /** No tunnel on the network the system reports to us. */
    object Inactive : VpnActiveStatus()

    /** A VPN tunnel is up. */
    data class Active(
        val viaCapabilities: Boolean,
        val vpnServicePresent: Boolean
    ) : VpnActiveStatus()

    /** ConnectivityManager is not available (unlikely). */
    object Unavailable : VpnActiveStatus()
}

/**
 * §2 — Active VPN Detection.
 *
 * Primary probe: a network is a VPN when the Android system does NOT flag
 * NET_CAPABILITY_NOT_VPN on that network. We read the default network's
 * capabilities — exactly what §2 asks for.
 *
 * Secondary cross-check: a foreground process from one of our discovered
 * BIND_VPN_SERVICE apps (weak signal on modern Android, see the comment in
 * [runningVpnServicePresent]).  Call from a background thread / coroutine.
 */
object VpnActiveDetector {

    fun checkNow(context: Context): VpnActiveStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return VpnActiveStatus.Unavailable

        val activeNetwork = cm.activeNetwork ?: return VpnActiveStatus.Inactive
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return VpnActiveStatus.Inactive

        // NET_CAPABILITY_NOT_VPN == false  ->  this network is a VPN tunnel.
        val tunnel = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        if (!tunnel) return VpnActiveStatus.Inactive

        return VpnActiveStatus.Active(
            viaCapabilities = true,
            vpnServicePresent = runningVpnServicePresent(context)
        )
    }

    /**
     * Weak secondary signal: a foreground process owned by a discovered VPN
     * app. WARNING (Android 10+): the foreground process list is restricted to
     * your own process, so this will almost always come back false on modern
     * devices — keep it as a best-effort cross-check only.
     */
    private fun runningVpnServicePresent(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
        @Suppress("DEPRECATION")
        val running = runCatching { am.runningAppProcesses }.getOrDefault(null) ?: return false
        val vpnPackages = VpnAppDiscovery.peek().mapTo(HashSet()) { it.packageName }
        if (vpnPackages.isEmpty()) return false
        return running.any {
            it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                it.pkgList.any { p -> p in vpnPackages }
        }
    }
}