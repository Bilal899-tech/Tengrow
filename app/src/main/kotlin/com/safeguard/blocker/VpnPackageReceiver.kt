package com.safeguard.blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Hooks ACTION_PACKAGE_ADDED/REPLACED/REMOVED so [VpnAppDiscovery]'s cache is
 * dropped the instant the installed-app pool changes. The next discover()
 * call then produces a fresh list — no background service needed.
 */
class VpnPackageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REMOVED -> VpnAppDiscovery.invalidate()
        }
    }
}