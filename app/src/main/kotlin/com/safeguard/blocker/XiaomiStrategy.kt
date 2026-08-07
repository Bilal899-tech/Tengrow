package com.safeguard.blocker

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Xiaomi / Redmi (MIUI). Recents is hosted by the launcher itself
 * (`com.miui.home`) with an activity class of its own.
 */
class XiaomiStrategy : OemRecentsStrategy {

    override fun id() = "XIAOMI"

    override fun isRecentsWindow(pkg: String?, className: String?): Boolean {
        // OEM-FRAGILE: MIUI keeps recents in the HOME package, NOT systemui.
        // On some POCO/MIUI 13 builds the window reports
        //   pkg=com.miui.home className=com.miui.home.launcher.RecentsView/
        //   RecentsActivity ; there is no single fixed name.
        if (pkg != "com.miui.home") return false
        val c = className ?: return false
        // OEM-FRAGILE: classToken "Recents" is present in both the classic
        // MIUI 10-11 RecentsActivity and the newer MIUI 12+ RecentsOverlay.
        return c.contains("Recents")
    }

    override fun buildDismissSwipes(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int,
        density: Float
    ): List<DismissSwipe> =
        // OEM-FRAGILE: MIUI uses a vertical throw to remove a recents card,
        // same as stock — the sideways stroke is the retry.
        RecentsGestureFactory.verticalThenSideways(node, screenWidth, screenHeight, density)
}