package com.safeguard.blocker

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Full-screen blocking surface shown whenever a VPN launch, VPN keyword, or
 * VPN activation is detected. The only exit is back to the home screen —
 * extending SafeGuard's own session requires a valid master-password unlock.
 *
 * In addition, when OUR blocking shield VPN (§5) has taken over the network,
 * this screen exposes the 10-second panic button: it stops the shield so the
 * owner is never locked out of their own connectivity.
 */
class VpnBlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpn_block)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVpnBlockClose)
            .setOnClickListener {
                // Owner session is active — leaving the block is permitted.
                finishAndRemoveTask()
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVpnBlockPanic)
            .setOnClickListener {
                // §5 panic: immediately stop our shield tunnel (countdown
                // safety — without this the user could be cut off from data).
                VpnShieldVpnService.stopNow(this)
                Toast.makeText(this, R.string.vpn_panic_toast, Toast.LENGTH_SHORT).show()
                finishAndRemoveTask()
            }
    }

    override fun onBackPressed() {
        // Swallow back — the block screen is intentionally non-dismissible.
        moveTaskToBack(true)
    }
}