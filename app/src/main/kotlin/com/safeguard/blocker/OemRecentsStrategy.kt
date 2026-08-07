package com.safeguard.blocker

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * A per-OEM policy for (a) recognising the system Recents/Overview window and
 * (b) building the swipe strokes used to dismiss the blocked task card.
 *
 * Dispatch flow is owned by [RecentsSwiper]; strategies stay stateless so they
 * can be instantiated cheaply on every detection.
 */
interface OemRecentsStrategy {

    /** Stable human-readable name, used for logging. */
    fun id(): String

    /**
     * True when the window delivered through TYPE_WINDOW_STATE_CHANGED
     * (described by [pkg] + [className]) is the system Recents/Overview.
     */
    fun isRecentsWindow(pkgName: String?, className: String?): Boolean

    /**
     * Ordered list of dismissal swipe attempts for a matched card. Attempts
     * are tried in order until the card disappears (see [RecentsSwiper]).
     */
    fun buildDismissSwipes(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int,
        density: Float
    ): List<DismissSwipe>
}

/** One swipe stroke against a recents card, in screen coordinates. */
data class DismissSwipe(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val kind: String,
    val durationMs: Long = 300L
)

/**
 * Shared builders. The "raise" distance of 300dp on the vertical stroke
 * (spec tweak: card top - 300dp) and the sideways fallback keep the stock
 * "swipe-up-to-dismiss" behaviour, then a right-drag for OEMs that dismiss
 * horizontally.
 */
object RecentsGestureFactory {

    private const val SWIPE_RAISE_DP = 300f
    private const val HORIZONTAL_DRAG_DP = 160f

    // OEM-FRAGILE: 300dp of travel above the card's top edge opens the stock
    // "swipe up to dismiss" affordance. Some OEMs (Samsung OneUI Recents)
    // reveal a "swipe up = clear all" dead-zone that can absorb the gesture;
    // the [vertical, horizontal] ordering below is the accepted compromise.
    fun verticalThenSideways(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int,
        density: Float
    ): List<DismissSwipe> {
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }
        val centerX = bounds.centerX().coerceIn(0, screenWidth)
        val centerY = bounds.centerY().coerceIn(0, screenHeight)
        val risePx = (SWIPE_RAISE_DP * density).toInt()
        val dragPx = (HORIZONTAL_DRAG_DP * density).toInt()

        val upY = (bounds.top - risePx).coerceAtLeast(0)
        val rightX = (bounds.right + dragPx).coerceAtMost(screenWidth)
        val leftX = (bounds.left - dragPx).coerceAtLeast(0)

        // OEM-FRAGILE: AOSP recents inverts (swipe-up dismisses); Samsung and
        // Xiaomi accept the same stroke. The right/left pass is only reached as
        // the retry when the vertical did not clear the card.
        return listOf(
            DismissSwipe(centerX, centerY, centerX, upY, kind = "up"),
            DismissSwipe(centerX, centerY, rightX, centerY, kind = "right"),
            DismissSwipe(centerX, centerY, leftX, centerY, kind = "left")
        )
    }
}