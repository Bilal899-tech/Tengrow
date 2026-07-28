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
        return "Device Admin protection is active. Enter SafeGuard master password to disable."
    }
}
