package com.safeguard.blocker

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log

/**
 * Lock-task fallback (NOT the default path).
 *
 * When the app happens to be Device Owner (enterprise/COPE deployments), we can
 * shrink the whole problem: pin the blocking activity with lock-task mode so
 * that during a block session there is no Recents at all — the most reliable
 * dismissal of a blocked card is never having it visible.
 *
 * Deliberately separate from [RecentsSwiper]; normal installs never hit this.
 */
object RecentsLockTaskFallback {

    /**
     * Returns true when the blocking activity is now pinned. Only works if the
     * app is the device owner and the activity reports isLockTaskPermitted.
     */
    fun tryPin(activity: Activity): Boolean {
        if (!isDeviceOwner(activity)) return false

        // OEM-FRAGILE: none here — lock-task is an AOSP mechanism, but as with
        // everything on Samsung Wearables/OneUI... still identical semantics.
        return runCatching {
            activity.startLockTask()
            true
        }.getOrElse {
            Log.w(TAG, "startLockTask failed", it)
            false
        }
    }

    /** Always release when leaving the blocking activity. */
    fun unpin(activity: Activity) {
        runCatching { activity.stopLockTask() }
    }

    private fun isDeviceOwner(activity: Activity): Boolean {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        return runCatching { dpm.isDeviceOwnerApp(activity.packageName) }.getOrDefault(false)
    }

    private const val TAG = "RecentsLockTask"
}