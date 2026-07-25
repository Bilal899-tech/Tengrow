package com.safeguard.blocker

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            AppSessionState.lock()
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val comp = ComponentName(context, DeviceAdminReceiver::class.java)
            val adminActive = runCatching { dpm.isAdminActive(comp) }.getOrDefault(false)
            val pwdSet = PasswordManager.isSet(context)
            val kwPrefs = context.getSharedPreferences("keywords", Context.MODE_PRIVATE)
            val kwCount = kwPrefs.getStringSet("list", null)?.size ?: 0
            val integrityOK = pwdSet && kwCount >= 0
            if (adminActive && pwdSet && integrityOK) {
                runCatching {
                    android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    )?.contains(context.packageName) == true
                }
            }
        }
    }
}
