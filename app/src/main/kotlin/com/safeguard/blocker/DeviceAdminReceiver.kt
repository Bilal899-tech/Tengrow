package com.safeguard.blocker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class DeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        AppSessionState.tempUnlock(12_000L)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Aggressive-deletion guard: removing Device Admin (which would allow
        // uninstalling the app) without a valid unlocked owner session starts
        // the strict cool-down timer immediately.
        if (!AppSessionState.isValid()) {
            PanicLockdown.start(context, PanicConfig.lockdownMs(context))
        }
        return "Enter SafeGuard master password to disable device admin protection."
    }
}