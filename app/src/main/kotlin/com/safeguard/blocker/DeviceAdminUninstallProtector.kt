package com.safeguard.blocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * §6 — Prevent protector-app uninstall via Device Admin.
 *
 *  - [protect]:  setUninstallBlocked(admin, our package, true) so the user
 *    cannot uninstall Tengrow without first revoking Device Admin (which the
 *    Accessibility layer defends).
 *  - [disableApp]: best-effort DeviceAdmin fallback used by
 *    [AccessibilityUninstallHelper] when a system VPN app cannot be
 *    uninstalled — hide it via setApplicationHidden (works for profile/device
 *    owners; for plain admins it throws and we return false so the UI can
 *    guide the user instead).
 */
object DeviceAdminUninstallProtector {

    private fun dpm(context: Context): DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    private fun adminComponent(context: Context): ComponentName =
        ComponentName(context, DeviceAdminReceiver::class.java)

    /** §6: block uninstallation of THIS app while admin is active. */
    fun protect(context: Context): Boolean {
        val manager = dpm(context) ?: return false
        val admin = adminComponent(context)
        if (!manager.isAdminActive(admin)) return false
        return runCatching {
            manager.setUninstallBlocked(admin, context.packageName, true)
            true
        }.getOrElse { false }
    }

    /** Reverse of [protect] — used when the user legitimately removes admin. */
    fun unprotect(context: Context) {
        val manager = dpm(context) ?: return
        val admin = adminComponent(context)
        if (!manager.isAdminActive(admin)) return
        runCatching { manager.setUninstallBlocked(admin, context.packageName, false) }
    }

    /**
     * System-app fallback: try to hide the package. Requires the caller to be
     * a device/profile owner; returns false (caller guides the user) otherwise.
     */
    fun disableApp(context: Context, packageName: String): Boolean {
        val manager = dpm(context) ?: return false
        val admin = adminComponent(context)
        return runCatching {
            manager.setApplicationHidden(admin, packageName, true)
        }.getOrDefault(false)
    }
}