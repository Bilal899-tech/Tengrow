package com.safeguard.blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

/**
 * Active VPN detection built on [ConnectivityManager].
 *
 * Two consumption models are provided:
 *  - A [VpnStateListener] callback for in-process consumers.
 *  - A broadcast [ACTION_VPN_STATE_CHANGED] (extra: [EXTRA_VPN_ACTIVE]) for
 *    manifest-registered receivers, so a killed app is still notified.
 *
 * Uses [NetworkRequest] with [NetworkCapabilities.TRANSPORT_VPN], which works
 * from API 21 up (registerDefaultNetworkCallback would require API 24).
 */
object VpnDetector {

    const val ACTION_VPN_STATE_CHANGED = "com.safeguard.blocker.action.VPN_STATE_CHANGED"
    const val EXTRA_VPN_ACTIVE = "com.safeguard.blocker.extra.VPN_ACTIVE"

    /** In-process callback interface. */
    interface VpnStateListener {
        fun onVpnStateChanged(active: Boolean)
    }

    @Volatile
    private var registered = false

    /**
     * One-shot check: is a VPN-backed network currently the best default, or
     * simply present? Returns true as soon as any active network advertises
     * [NetworkCapabilities.TRANSPORT_VPN].
     */
    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val all = cm.allNetworks
        for (network in all) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true
        }
        return false
    }

    /**
     * Registers a network callback that observes VPN transport transitions.
     * Safe to call multiple times; only one callback is registered per process.
     */
    @Synchronized
    fun startMonitoring(context: Context, listener: VpnStateListener? = null) {
        val cm = context.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        if (registered) return
        registered = true

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun dispatch(context: Context) {
                val active = isVpnActive(context)
                listener?.onVpnStateChanged(active)
                context.sendBroadcast(
                    Intent(ACTION_VPN_STATE_CHANGED)
                        .setPackage(context.packageName)
                        .putExtra(EXTRA_VPN_ACTIVE, active)
                )
            }

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                dispatch(context)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                // Recompute the global state (not just this network's), so a
                // parallel non-VPN network change can never emit a false VPN
                // down/up signal while another VPN remains active.
                dispatch(context)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                dispatch(context)
            }
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                cm.registerNetworkCallback(request, callback)
            }
        }
    }

    /** Manifest-registered convenience receiver — see [VpnDetector.ACTION_VPN_STATE_CHANGED]. */
    class VpnStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val active = intent.getBooleanExtra(EXTRA_VPN_ACTIVE, false)
            VpnBlockerService.handleExternalVpnState(context, active)
        }
    }

    /** Returns an [IntentFilter] matching the VPN state broadcast. */
    fun vpnStateFilter(): IntentFilter = IntentFilter(ACTION_VPN_STATE_CHANGED)
}
