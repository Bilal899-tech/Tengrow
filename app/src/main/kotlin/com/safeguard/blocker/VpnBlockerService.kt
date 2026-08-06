package com.safeguard.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Dedicated accessibility service for the anti-VPN module.
 *
 * Two enforcement layers:
 *  1. Known VPN apps (see [VpnPackageDatabase]) that are LAUNCHED are
 *     immediately bounced home and replaced with the full-screen
 *     [VpnBlockActivity].
 *  2. VPN-related words ("vpn", "openvpn", ...) appearing anywhere in the
 *     foreground UI (Play Store search, Chrome address bar, an app's install
 *     screen) trigger the same block.
 *
 * Uses the same session-lock semantics as the rest of the app so the owner
 * can open SafeGuard's own screens without being looped (a 20-minute
 * temporary unlock, refreshed on every block, keeps the block sticky).
 */
class VpnBlockerService : AccessibilityService() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile private var lastPkgBlockMs = 0L
    @Volatile private var lastKeywordBlockMs = 0L
    @Volatile private var lastScanHash = 0
    @Volatile private var blockedSince = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""
        if (pkg.isEmpty() || pkg == packageName) return

        // 1) Direct launch of a known VPN app.
        if (pkg in VpnPackageDatabase.KNOWN_VPN_PACKAGES) {
            block("package_launch", pkg)
            return
        }

        // 2) Keyword scan of the visible window.
        val now = System.currentTimeMillis()
        val cooldown = if (pkg in SCAN_PACKAGES) SCAN_COOLDOWN_MS else KEYWORD_COOLDOWN_MS
        if (now - lastKeywordBlockMs < cooldown) return

        val source = event.source
        try {
            val combined = StringBuilder(
                event.text?.joinToString(" ") ?: ""
            )
            if (source != null) {
                val nodeText = gatherNodeText(source, maxDepth = 18)
                if (nodeText.isNotEmpty()) combined.append(' ').append(nodeText)
            }
            val normalized = KeywordNormalizer.normalize(combined.toString())
            if (normalized.isEmpty()) return
            val hash = normalized.hashCode()
            if (hash == lastScanHash) return
            lastScanHash = hash

            for (keyword in VpnPackageDatabase.VPN_KEYWORDS) {
                if (normalized.contains(keyword)) {
                    block("keyword", keyword)
                    break
                }
            }
        } finally {
            runCatching { source?.recycle() }
        }
    }

    private fun block(reason: String, detail: String) {
        val now = System.currentTimeMillis()
        // Global debounce — one block per event burst.
        if (now - lastPkgBlockMs < GLOBAL_BLOCK_COOLDOWN_MS) return
        lastPkgBlockMs = now

        blockedSince = now
        AppSessionState.tempUnlock(VPN_BLOCK_UNLOCK_MS)

        performGlobalAction(GLOBAL_ACTION_HOME)

        handler.postDelayed({
            val i = Intent(this, VpnBlockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra("reason", reason)
                putExtra("detail", detail)
            }
            runCatching { startActivity(i) }
        }, 180L)
    }

    private fun gatherNodeText(node: AccessibilityNodeInfo, maxDepth: Int, depth: Int = 0): String {
        if (depth > maxDepth) return ""
        val sb = StringBuilder(96)
        val t = node.text?.toString()
        val d = node.contentDescription?.toString()
        if (!t.isNullOrEmpty()) { sb.append(' '); sb.append(t.lowercase()) }
        if (!d.isNullOrEmpty()) { sb.append(' '); sb.append(d.lowercase()) }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            try {
                sb.append(gatherNodeText(child, maxDepth, depth + 1))
            } catch (_: Exception) {
            } finally {
                runCatching { child.recycle() }
            }
            if (sb.length > 4000) break
        }
        return sb.toString()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val GLOBAL_BLOCK_COOLDOWN_MS = 1_200L
        private const val KEYWORD_COOLDOWN_MS = 2_500L
        private const val SCAN_COOLDOWN_MS = 1_200L
        private const val VPN_BLOCK_UNLOCK_MS = 20 * 60_000L

        /** Apps we scan aggressively (store searches, installers, browsers). */
        private val SCAN_PACKAGES = setOf(
            "com.android.vending",           // Google Play Store
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.android.chrome",
            "org.chromium.chrome",
            "org.mozilla.firefox",
            "com.microsoft.edge",
            "com.brave.browser"
        )

        /** React to a VPN-activation broadcast from [VpnMonitorService]. */
        @JvmStatic
        fun handleExternalVpnState(context: Context, active: Boolean) {
            if (!active) return
            // Only escalate when the session is locked (owner not in-app).
            if (AppSessionState.isValid()) return
            val i = Intent(context, VpnBlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("reason", "vpn_connected")
            }
            context.startActivity(i)
        }
    }
}
