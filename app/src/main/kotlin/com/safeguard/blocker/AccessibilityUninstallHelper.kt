package com.safeguard.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** Result of the automated uninstall/disable flow (§4). */
sealed class UninstallAutomationResult {
    /** The package is no longer installed. */
    object Uninstalled : UninstallAutomationResult()

    /** System app that could not be uninstalled — now disabled/hidden instead. */
    object Disabled : UninstallAutomationResult()

    /** The automation aborted (timeout, missing node, or crash). */
    data class Failed(val step: String, val reason: String) : UninstallAutomationResult()
}

/**
 * §4 — Auto-Delete VPN via Accessibility (Critical).
 *
 * Silent uninstall is impossible on Android (no such API exists, even with
 * Device Admin), so this helper drives the *system's own* uninstall UI through
 * the granted AccessibilityService:
 *
 *  1. Open App details: Settings.ACTION_APPLICATION_DETAILS_SETTINGS.
 *  2. Wait (≤5s) for the settings window (com.android.settings …).
 *  3. Find & click "Uninstall"/"Remove"/"Disable app" (text variations).
 *  4. Wait for the system confirmation dialog (packageinstaller & friends).
 *  5. Click the positive button ("OK", "Uninstall", "Yes", …).
 *  6. Verify the package is gone; for system apps fall back to the
 *     DeviceAdmin disable path ([DeviceAdminUninstallProtector]) or guide the
 *     user.
 *
 * Every step is bounded by a 5-second timeout; any failure aborts the whole
 * flow, notifies the user and logs the incident to the encrypted log file
 * ([VpnActionLog]), never to Logcat (§4).
 */
class AccessibilityUninstallHelper(private val service: AccessibilityService) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lastWindowPackage = AtomicReference<String?>(null)

    /** True while an automation run is in flight (used to suppress other logic). */
    @Volatile
    var isActive: Boolean = false
        private set

    /** Feed TYPE_WINDOW_STATE_CHANGED events here to accelerate the waits. */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastWindowPackage.set(event.packageName?.toString())
        }
    }

    /** Launch the full §4 flow for [packageName]. */
    fun start(packageName: String, isSystemApp: Boolean) {
        if (isActive) return
        isActive = true
        scope.launch {
            val result = run(packageName, isSystemApp)
            isActive = false
            notify(service, result)
        }
    }

    private suspend fun run(packageName: String, isSystemApp: Boolean): UninstallAutomationResult {

        VpnActionLog.record(service, "BEGIN uninstall flow: $packageName (system=$isSystemApp)")

        // 1 — open App details
        val launched = runCatching {
            service.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        if (launched.isFailure) {
            VpnActionLog.record(service, "FAIL open_settings ${launched.exceptionOrNull()}")
            return UninstallAutomationResult.Failed("open_settings", "could not open App settings")
        }
        VpnActionLog.record(service, "OK open_settings $packageName")
        delay(400)

        // 2 — wait for the settings window
        val settingsWindow = waitForWindow(timeoutMs = STEP_TIMEOUT_MS) { pkg ->
            pkg in SETTINGS_PACKAGES
        }
        if (!settingsWindow) {
            VpnActionLog.record(service, "FAIL wait_settings last=$lastWindowPackage")
            return UninstallAutomationResult.Failed("wait_settings", "settings window never appeared")
        }
        VpnActionLog.record(service, "OK wait_settings")

        // 3 — tap Uninstall / Remove / Disable app
        val uninstallNode = findTextNode(UNINSTALL_BUTTON_TEXTS) ?: run {
            VpnActionLog.record(service, "FAIL tap_uninstall (button not found)")
            return UninstallAutomationResult.Failed("tap_uninstall", "button not found")
        }
        val clickable = findClickableAncestor(uninstallNode)
        val clicked = clickNode(clickable ?: uninstallNode)
        VpnActionLog.record(service, "tap_uninstall clicked=$clicked")
        if (!clicked) return UninstallAutomationResult.Failed("tap_uninstall", "ACTION_CLICK failed")

        // 4 — wait for the confirmation dialog
        val dialogSeen = waitForWindow(timeoutMs = STEP_TIMEOUT_MS) { pkg ->
            pkg in DIALOG_PACKAGES
        }
        VpnActionLog.record(service, "wait_dialog seen=$dialogSeen")
        if (!dialogSeen) {
            // Some OEMs show the confirm inline without a package change — try
            // a direct text scan before declaring failure.
            val inlineConfirm = findTextNode(CONFIRM_BUTTON_TEXTS) ?: run {
                VpnActionLog.record(service, "FAIL wait_dialog (no dialog)")
                return UninstallAutomationResult.Failed("wait_dialog", "no confirmation dialog appeared")
            }
            val inlineOk = clickNode(findClickableAncestor(inlineConfirm) ?: inlineConfirm)
            VpnActionLog.record(service, "tap_confirm_inline clicked=$inlineOk")
            if (!inlineOk) return UninstallAutomationResult.Failed("tap_confirm", "inline confirm failed")
        } else {
            // 5 — click the positive button of the dialog
            val confirmNode = findTextNode(CONFIRM_BUTTON_TEXTS) ?: run {
                VpnActionLog.record(service, "FAIL tap_confirm (no positive button)")
                return UninstallAutomationResult.Failed("tap_confirm", "positive button not found")
            }
            val confirmed = clickNode(findClickableAncestor(confirmNode) ?: confirmNode)
            VpnActionLog.record(service, "tap_confirm clicked=$confirmed")
            if (!confirmed) return UninstallAutomationResult.Failed("tap_confirm", "ACTION_CLICK failed")
        }

        // 5 — verify the package disappeared (or got disabled for system apps).
        val gone = waitForTrue(timeoutMs = STEP_TIMEOUT_MS) {
            runCatching {
                val ai = service.packageManager.getApplicationInfo(packageName, 0)
                !ai.enabled
            }.getOrDefault(true)
        }
        VpnActionLog.record(service, "verify_uninstalled gone=$gone")
        if (gone) {
            // presence: null = uninstalled, false = installed-but-disabled,
            //           true  = still fully present.
            val presence = runCatching {
                service.packageManager.getApplicationInfo(packageName, 0).enabled
            }.getOrNull()
            VpnAppDiscovery.invalidate()
            return when (presence) {
                null -> UninstallAutomationResult.Uninstalled
                false -> UninstallAutomationResult.Disabled
                true -> UninstallAutomationResult.Failed("verify", "app still present and enabled")
            }
        }

        // 6b — system-app fallback via DeviceAdmin disable.
        VpnActionLog.record(service, "dpm_fallback needed isSystemApp=$isSystemApp")
        return if (isSystemApp && DeviceAdminUninstallProtector.disableApp(service, packageName)) {
            UninstallAutomationResult.Disabled
        } else {
            UninstallAutomationResult.Failed("dpm_fallback", "app still installed; user guidance required")
        }
    }

    // ------------------------------------------------------------------
    // Window / node helpers
    // ------------------------------------------------------------------

    private suspend fun waitForWindow(timeoutMs: Long, predicate: (String) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val alive = service.rootInActiveWindow
            if (alive != null) {
                val pkg = alive.packageName?.toString()
                runCatching { alive.recycle() }
                if (pkg != null && predicate(pkg)) return true
            }
            // Accelerate with the event-driven hint (TYPE_WINDOW_STATE_CHANGED).
            val ev = lastWindowPackage.get()
            if (ev != null && predicate(ev)) return true
            delay(120)
        }
        return false
    }

    private suspend fun waitForTrue(timeoutMs: Long, check: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (runCatching { check() }.getOrDefault(false)) return true
            delay(150)
        }
        return false
    }

    private fun findTextNode(accepted: Set<String>): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val found = findDeep(root, accepted)
        // Free the root unless it *is* the matched node (caller owns that).
        if (found !== root) runCatching { root.recycle() }
        return found
    }

    private fun findDeep(node: AccessibilityNodeInfo, accepted: Set<String>): AccessibilityNodeInfo? {
        val label = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        if (accepted.contains(label.trim().lowercase())) return node
        val count = runCatching { node.childCount }.getOrDefault(0)
        for (i in 0 until count) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val hit = runCatching { findDeep(child, accepted) }.getOrNull()
            // Each visited child is independent of the returned hit; recycle it
            // as soon as its subtree is done to avoid leaking node wrappers.
            runCatching { child.recycle() }
            if (hit != null) return hit
        }
        return null
    }

    /** Walk up until a node that can actually receive the click. */
    private fun findClickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            val cursor = current
            if (runCatching { cursor.isClickable }.getOrDefault(false)) return cursor
            current = runCatching { cursor.parent }.getOrNull()
            runCatching { cursor.recycle() }
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val ok = runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
        // The caller never uses the node after clicking — release it here so
        // neither the resolved ancestor nor the fallback leaks.
        runCatching { node.recycle() }
        return ok
    }

    // ------------------------------------------------------------------
    // Notification on failure / completion (§4: notify the user)
    // ------------------------------------------------------------------

    private fun notify(context: Context, result: UninstallAutomationResult) {
        val msg = when (result) {
            is UninstallAutomationResult.Uninstalled -> "VPN app uninstalled automatically"
            is UninstallAutomationResult.Disabled -> "VPN app disabled (system app)"
            is UninstallAutomationResult.Failed -> "VPN removal failed at step '$result.step': ${result.reason}"
        }
        VpnActionLog.record(context, "final=$result message=$msg")
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(
                        CHANNEL_ID, "VPN Automation", android.app.NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
            val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("VPN Guard")
                .setContentText(msg)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(6001, notification)
        }
    }

    fun destroy() {
        scope.cancel()
        isActive = false
    }

    companion object {
        private const val STEP_TIMEOUT_MS = 5_000L   // §4: 5s per step
        private const val CHANNEL_ID = "vpn_automation"

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.app.cocktailbarservice"
        )

        private val DIALOG_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.android.settings"
        )

        /** §4 step 3 text variations. */
        private val UNINSTALL_BUTTON_TEXTS = setOf(
            "uninstall", "remove", "disable app", "disable"
        )

        /** §4 step 5 positive-button variations. */
        private val CONFIRM_BUTTON_TEXTS = setOf(
            "ok", "uninstall", "yes", "remove", "delete",
            "disable app", "disable", "turn off", "confirm"
        )
    }
}