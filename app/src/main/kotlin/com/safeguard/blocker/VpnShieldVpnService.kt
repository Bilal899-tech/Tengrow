package com.safeguard.blocker

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor

/**
 * §5 — Network-level VPN block.
 *
 * Our own internal VpnService with a dummy tunnel
 * (Builder.addAddress("192.0.2.1", 24).establish()). Android only ever runs
 * ONE VPN per user, so establishing this tunnel kicks the external VPN off the
 * device — the external tunnel is broken until it is disabled.
 *
 * To avoid locking the owner out of their own device, a "panic" stop path is
 * provided: [panicStopWithCountdown] shuts the shield down after N seconds
 * (default 10), and pressing the panic button in the UI stops it immediately
 * (§5 "panic button … within 10 seconds").
 */
class VpnShieldVpnService : VpnService() {

    private var tunnelFd: ParcelFileDescriptor? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startShield()
            ACTION_STOP, ACTION_PANIC -> stopShieldAndSelf()
        }
        return START_NOT_STICKY
    }

    private fun startShield() {
        if (tunnelFd != null) return
        val builder = Builder()
            .setSession(SESSION_NAME)
            .addAddress("192.0.2.1", 24)   // TEST-NET-1 dummy address (§5)
        val fd = runCatching { builder.establish() }.getOrNull()
        if (fd == null) {
            VpnActionLog.record(this, "shield establish() FAILED (consent or VPN already owned)")
            stopSelf()
            return
        }
        tunnelFd = fd
        VpnActionLog.record(this, "shield tunnel established")
    }

    private fun stopShieldAndSelf() {
        tunnelFd?.let { runCatching { it.close() } }
        tunnelFd = null
        VpnActionLog.record(this, "shield tunnel stopped")
        stopSelf()
    }

    override fun onRevoke() {
        // The system took the VPN away from us (user revoked, or the external
        // VPN re-established). Nothing to clean except the fd.
        tunnelFd?.let { runCatching { it.close() } }
        tunnelFd = null
        super.onRevoke()
    }

    override fun onDestroy() {
        stopShieldAndSelf()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val SESSION_NAME = "Tengrow Shield"
        private const val ACTION_START = "com.safeguard.blocker.action.SHIELD_START"
        private const val ACTION_STOP = "com.safeguard.blocker.action.SHIELD_STOP"
        private const val ACTION_PANIC = "com.safeguard.blocker.action.SHIELD_PANIC"

        private val handler = Handler(Looper.getMainLooper())
        private var pendingPanicAt = 0L

        /**
         * Ask the system to start the shield. First time requires user consent
         * (VpnService.prepare) — afterwards the tunnel establishes directly.
         */
        fun startShield(context: Context) {
            val prepare = VpnService.prepare(context)
            if (prepare == null) {
                start(context, ACTION_START)
            } else {
                prepare.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(prepare) }
            }
        }

        /** Immediate stop (panic button). */
        fun stopNow(context: Context) {
            cancelPendingPanic()
            start(context, ACTION_PANIC)
        }

        /**
         * Stop the shield automatically after [timeoutSeconds] if the user
         * does not hit the panic button first (§5). Each call replaces the
         * previous pending panic.
         */
        fun panicStopWithCountdown(context: Context, timeoutSeconds: Int = 10) {
            cancelPendingPanic()
            pendingPanicAt = System.currentTimeMillis() + timeoutSeconds * 1000L
            handler.postDelayed({
                if (pendingPanicAt == 0L) return@postDelayed
                pendingPanicAt = 0L
                start(context, ACTION_PANIC)
            }, timeoutSeconds * 1000L)
        }

        private fun cancelPendingPanic() {
            handler.removeCallbacksAndMessages(null)
            pendingPanicAt = 0L
        }

        /** Seconds remaining before an automatic panic stop fires (0 = none). */
        fun panicRemainingSeconds(): Int {
            if (pendingPanicAt == 0L) return 0
            return ((pendingPanicAt - System.currentTimeMillis()) / 1000).toInt()
                .coerceAtLeast(0)
        }

        private fun start(context: Context, action: String) {
            runCatching {
                context.startService(
                    Intent(context, VpnShieldVpnService::class.java).setAction(action)
                )
            }
        }
    }
}