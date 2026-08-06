package com.safeguard.blocker

import android.app.Activity
import android.os.Bundle

/**
 * Full-screen blocking surface shown whenever a VPN launch, VPN keyword, or
 * VPN activation is detected. The only exit is back to the home screen —
 * extending SafeGuard's own session requires a valid master-password unlock.
 */
class VpnBlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpn_block)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVpnBlockClose)
            .setOnClickListener {
                finishAndRemoveTask()
            }
    }

    override fun onBackPressed() {
        // Swallow back — the block screen is intentionally non-dismissible.
        moveTaskToBack(true)
    }
}