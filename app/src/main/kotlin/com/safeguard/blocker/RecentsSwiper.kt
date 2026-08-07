package com.safeguard.blocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Turns the freshly-blocked package queue into actual swipe gestures against
 * the Recents/Overview screen.
 *
 * Flow (invoked from the accessibility service on a TYPE_WINDOW_STATE_CHANGED
 * event that [OemDetector] recognised as Recents):
 *   1. read pending blocked packages from [RecentsCleaner];
 *   2. if none -> do nothing (user opened recents manually, nothing to remove);
 *   3. find the matching card in the active window tree;
 *   4. dispatch one [DismissSwipe] from the strategy;
 *   5. after 500ms re-check whether the card is still there:
 *        - vanished    -> clear the queue entry, move on;
 *        - still there -> try the next swipe from the strategy list;
 *   6. exhausted -> clear the queue entry and log the failure.
 *
 * Rate limit: at most [MAX_SWIPES_PER_WINDOW] gesture dispatches per
 * [RATE_WINDOW_MS], otherwise we bail out and let the next Recents opening
 * finish the job. All work runs on the main thread to keep
 * [AccessibilityService] callbacks happy.
 */
class RecentsSwiper(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var inFlight = false
    private val swipeTimes = ArrayDeque<Long>()

    private val density = context.resources.displayMetrics.density
    private val screenWidth = context.resources.displayMetrics.widthPixels
    private val screenHeight = context.resources.displayMetrics.heightPixels

    /** Entry point from the accessibility service. */
    fun startIfRecents(service: AccessibilityService) {
        if (inFlight) return
        val pending = RecentsCleaner.pendingPackages(context)
        if (pending.isEmpty()) {
            // §6: user opened recents manually and no blocked app was launched
            // recently -> nothing to do.
            return
        }
        if (Build.VERSION.SDK_INT < 24) {
            // dispatchGesture/GestureDescription need API 24 (Nougat).
            Log.w(TAG, "dispatchGesture unavailable on API ${Build.VERSION.SDK_INT}; skipping")
            return
        }
        if (!rateLimitOk()) return
        inFlight = true
        runCatching { process(service, pending, 0) }
            .onFailure {
                Log.w(TAG, "Recents pass failed", it)
                inFlight = false
            }
    }

    // ------------------------------------------------------------------
    // Sequential worker
    // ------------------------------------------------------------------

    private fun process(service: AccessibilityService, pending: List<String>, index: Int) {
        if (index >= pending.size) {
            inFlight = false
            return
        }
        val pkg = pending[index]
        val label = resolveAppLabel(pkg)

        val root = service.rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "No active window to inspect for $pkg")
            inFlight = false
            return
        }

        val node = findBlockedCard(root, pkg, label)
        if (node == null) {
            // Card already absent -> nothing to do for this entry (§6 "do
            // nothing" case). Clear it and continue with the next package.
            RecentsCleaner.clear(context, pkg)
            runCatching { root.recycle() }
            process(service, pending, index + 1)
            return
        }

        val swipes = try {
            OemDetector.strategyFor(Build.MANUFACTURER)
                .buildDismissSwipes(node, screenWidth, screenHeight, density)
        } catch (e: Exception) {
            Log.w(TAG, "Gesture building failed for $pkg", e)
            emptyList()
        }

        runCatching { node.recycle() }
        runCatching { root.recycle() }

        if (swipes.isEmpty()) {
            RecentsCleaner.clear(context, pkg)
            Log.w(TAG, "No usable swipe for $pkg; skipping")
            process(service, pending, index + 1)
            return
        }
        swipeAndVerify(service, pending, index, pkg, label, swipes, attempt = 0)
    }

    private fun swipeAndVerify(
        service: AccessibilityService,
        pending: List<String>,
        index: Int,
        pkg: String,
        label: String?,
        swipes: List<DismissSwipe>,
        attempt: Int
    ) {
        // Out of swipe candidates -> clear + log failure (§3 "If still failing").
        if (attempt >= swipes.size) {
            Log.w(TAG, "All $attempt swipe attempts failed for $pkg")
            RecentsCleaner.clear(context, pkg)
            process(service, pending, index + 1)
            return
        }

        if (!rateLimitOk()) {
            // Rate limited: abort this run, keep the entry queued so the next
            // Recents opening retries it.
            Log.i(TAG, "Rate limited; aborting run (${swipeTimes.size}/$MAX_SWIPES_PER_WINDOW used)")
            inFlight = false
            return
        }

        val swipe = swipes[attempt]
        noteSwipeTime()

        val gesture = try {
            val path = Path().apply {
                moveTo(swipe.startX.toFloat(), swipe.startY.toFloat())
                lineTo(swipe.endX.toFloat(), swipe.endY.toFloat())
            }
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, swipe.durationMs))
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Gesture build error", e)
            swipeAndVerify(service, pending, index, pkg, label, swipes, attempt + 1)
            return
        }

        val dispatched = try {
            service.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "dispatchGesture threw for $pkg", e)
            false
        }

        if (!dispatched) {
            Log.w(TAG, "dispatchGesture refused for $pkg (${swipe.kind})")
            swipeAndVerify(service, pending, index, pkg, label, swipes, attempt + 1)
            return
        }

        Log.i(TAG, "Swiped ${swipe.kind} for $pkg (attempt ${attempt + 1}/${swipes.size})")
        handler.postDelayed(
            { verifyCard(service, pending, index, pkg, label, swipes, attempt) },
            500L
        )
    }

    /**
     * 500ms post-swipe check: card still there? If yes, move to the next
     * attempt (horizontal OEM fallback). If no, the swipe worked.
     */
    private fun verifyCard(
        service: AccessibilityService,
        pending: List<String>,
        index: Int,
        pkg: String,
        label: String?,
        swipes: List<DismissSwipe>,
        attempt: Int
    ) {
        val root = service.rootInActiveWindow
        if (root == null) {
            // Window was torn down mid-action — treat as handled.
            RecentsCleaner.clear(context, pkg)
            inFlight = false
            return
        }
        val stillThere = findBlockedCard(root, pkg, label)
        runCatching { root.recycle() }

        if (stillThere == null) {
            // Card vanished -> success (§3).
            RecentsCleaner.clear(context, pkg)
            Log.i(TAG, "Recents card removed for $pkg")
            process(service, pending, index + 1)
        } else {
            runCatching { stillThere.recycle() }
            // Card persists -> retry with the next swipe direction (§3).
            swipeAndVerify(service, pending, index, pkg, label, swipes, attempt + 1)
        }
    }

    // ------------------------------------------------------------------
    // Node search helpers
    // ------------------------------------------------------------------

    /**
     * Depth-first search for a recents card mentioning the blocked package or
     * its user-facing app label. Prefers strong matches (package token) over
     * a label-only match, and the widest card in a tie.
     *
     * Every node visited is a fresh [AccessibilityNodeInfo] obtained from
     * [AccessibilityNodeInfo.parent]/[AccessibilityNodeInfo.getChild], so each
     * one is recycled as soon as its subtree is fully explored — with the
     * exception of the surviving candidate(s), which stay alive so the caller
     * can read their bounds (the caller recycles the winner).
     */
    private fun findBlockedCard(
        root: AccessibilityNodeInfo,
        packageName: String,
        appLabel: String?
    ): AccessibilityNodeInfo? {
        val pkg = packageName.lowercase()
        val label = appLabel?.lowercase()
        val candidates = ArrayList<Pair<AccessibilityNodeInfo, Int>>()

        // Returns true when [node] itself became a candidate (i.e. its bounds
        // are still needed later and it must not be recycled).
        fun walk(node: AccessibilityNodeInfo, depth: Int): Boolean {
            if (depth > MAX_TREE_DEPTH) return false
            val text = StringBuilder(96)
            runCatching {
                node.text?.let { text.append(it).append(' ') }
                node.contentDescription?.let { text.append(it).append(' ') }
            }
            val lt = text.toString().lowercase()
            var score = 0
            if (lt.contains(pkg)) score += PKG_MATCH_SCORE
            if (!label.isNullOrEmpty() && lt.contains(label)) score += LABEL_MATCH_SCORE
            val added = score > 0
            if (added) candidates.add(node to score)
            val count = runCatching { node.childCount }.getOrDefault(0)
            for (i in 0 until count) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                val childKept = runCatching { walk(child, depth + 1) }.getOrDefault(false)
                // A child that never became a candidate is dead once its
                // subtree is explored. Candidates live on for the bounds read.
                if (!childKept) runCatching { child.recycle() }
            }
            return added
        }

        runCatching { walk(root, 0) }
        if (candidates.isEmpty()) return null

        val best = candidates.maxWithOrNull { a, b ->
            if (a.second != b.second) a.second - b.second
            else a.first.boundsInScreenWidth() - b.first.boundsInScreenWidth()
        }

        // Free everything we did not pick; only the winner survives for the
        // caller to read bounds from (the caller recycles it).
        for ((n, _) in candidates) {
            if (n !== best?.first) runCatching { n.recycle() }
        }
        return best?.first
    }

    private fun AccessibilityNodeInfo.boundsInScreenWidth(): Int {
        val b = Rect()
        runCatching { getBoundsInScreen(b) }
        return b.width()
    }

    // ------------------------------------------------------------------
    // App label + rate limiting
    // ------------------------------------------------------------------

    private fun resolveAppLabel(pkg: String): String? =
        runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrNull()

    private fun rateLimitOk(): Boolean {
        val now = System.currentTimeMillis()
        while (swipeTimes.isNotEmpty() && now - swipeTimes.first() > RATE_WINDOW_MS) {
            swipeTimes.removeFirst()
        }
        return swipeTimes.size < MAX_SWIPES_PER_WINDOW
    }

    private fun noteSwipeTime() = swipeTimes.addLast(System.currentTimeMillis())

    companion object {
        private const val TAG = "RecentsSwiper"

        /** Try at most 3 gestures per 10s (§6 rate limit). */
        private const val MAX_SWIPES_PER_WINDOW = 3
        private const val RATE_WINDOW_MS = 10_000L

        private const val MAX_TREE_DEPTH = 26

        /** Package-name match is decisive (recents cards usually expose the label). */
        private const val PKG_MATCH_SCORE = 3
        private const val LABEL_MATCH_SCORE = 2
    }
}