package com.safeguard.blocker

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Stock/AOSP Android Recents (Pixel, OnePlus, Motorola, Nokia, Sony and other
 * near-stock OEMs).
 */
class StockAndroidStrategy : OemRecentsStrategy {

    override fun id() = "STOCK"

    override fun isRecentsWindow(pkg: String?, className: String?): Boolean {
        // OEM-FRAGILE: stock recents lives inside com.android.systemui. The
        // className varies by Android version — e.g.
        //   * Android 10  -> com.android.systemui.recents.RecentsActivity
        //   * Android 11+ -> com.android.systemui.recents.RecentsActivity /
        //                    com.android.systemui.taskview / .. use RecentsActivity
        // OnePlus on Hydrogen/Oxygen exposes the SAME PACKAGE but a different
        // class, so we key on package + class substrings, not exact names.
        if (pkg != "com.android.systemui") return false
        val c = className ?: return false
        // OEM-FRAGILE: some builds report the overview surface as
        // "RecentsActivity", others as "...LaunchRecentsActivity" or
        // "...RecentsActivity"; keep the substring matching deliberately loose.
        return c.contains("Recents") || c.contains("Overview")
    }

    override fun buildDismissSwipes(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int,
        density: Float
    ): List<DismissSwipe> =
        // OEM-FRAGILE: stock = swipe-up-to-dismiss; rightward is the OEM retry.
        RecentsGestureFactory.verticalThenSideways(node, screenWidth, screenHeight, density)
}