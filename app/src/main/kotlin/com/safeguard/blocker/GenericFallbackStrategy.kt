package com.safeguard.blocker

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Unknown / unhandled manufacturer. We never swear we recognised the recents
 * screen; we only try when the window class itself strongly smells like an
 * overview surface, and we ignore our own package to avoid loops.
 */
class GenericFallbackStrategy : OemRecentsStrategy {

    override fun id() = "GENERIC"

    override fun isRecentsWindow(pkg: String?, className: String?): Boolean {
        // Safety: never treat our own surface as Recents (self-loop guard).
        if (pkg != null && pkg.startsWith(OUR_PACKAGE)) return false
        val c = className ?: return false
        // OEM-FRAGILE: no manufacturer manifest, so we can only match on generic
        // class names that strongly smell like an overview surface.
        return c.contains("Recents") || c.contains("Overview") || c.contains("RecentTask")
    }

    override fun buildDismissSwipes(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int,
        density: Float
    ): List<DismissSwipe> =
        // OEM-FRAGILE: we cannot know the OEM's dismissal axis, so we try the
        // neutral up-stroke first, then drag right, then drag left (§3 contract:
        // vertical first, one horizontal retry, second horizontal as bonus).
        RecentsGestureFactory.verticalThenSideways(node, screenWidth, screenHeight, density)

    companion object {
        // applicationId from build.gradle.kts — self-loop guard for the
        // "ignore our own package" safety requirement (§6).
        private const val OUR_PACKAGE = "com.tengrow.app"
    }
}