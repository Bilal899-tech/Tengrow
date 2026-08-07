package com.safeguard.blocker

/**
 * A single VPN-capable application discovered on the device (§1).
 *
 * Detection is cheap and deterministic: the app declares a Service with the
 * `android.net.VpnService` action AND android.permission.BIND_VPN_SERVICE
 * (which is what [android.net.VpnService] requires in its manifest). We do NOT
 * rely on a name list, so brand-new or renamed VPN client apps are found too.
 */
data class VpnApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)