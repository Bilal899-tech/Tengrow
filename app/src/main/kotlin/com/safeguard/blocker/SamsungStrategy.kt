package com.safeguard.blocker

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Samsung OneUI / older TouchWiz Recents.
 *
 * Samsung routes recents through com.android.systemui in current OneUI, but on
 * many devices the recents surfaced used to come from the "cocktail bar"
 * service. We accept both so detection keeps working across the refresh.
 */
class SamsungStrategy : OemRecentsStrategy {

    override fun id() = "SAMSUNG"

    // OEM-FRAGILE: Samsung has shipped multiple recents hosts across years:
    // com.samsung.android.app.cocktailbarservice (Android 8/9/10 era) and
    // com.android.systemui (OneUI 3+/Android 13+). The className also changed
    // ("RecentsActivity" -> "RecentTasksActivity"). Any of them must match.
    private val recentsPackages = setOf(
        "com.samsung.android.app.cocktailbarservice",
        "com.android.systemui"
    )

    override fun isRecentsWindow(pkg: String?, className: String?): Boolean {
        if (pkg !in recentsPackages) return false
        val c = className ?: return false
        // OEM-FRAGILE: OneUI classes observed: "...RecentsActivity",
        // "...RecentTasksActivity", "...HomeScreenController$Dex..." etc.
        return c.contains("Recents") || c.contains("RecentTask") || c.contains("Overview")
    }

    override fun buildDismissSwipes(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int,
        density: Float
    ): List<DismissSwipe> =
        // OEM-FRAGILE: OneUI cards are dismissed with a vertical throw on most
        // builds, but "swipe horizontally" is valid on recent versions too;
        // keeping up-first then sideways matches the retry contract in §3.
        RecentsGestureFactory.verticalThenSideways(node, screenWidth, screenHeight, density)
}