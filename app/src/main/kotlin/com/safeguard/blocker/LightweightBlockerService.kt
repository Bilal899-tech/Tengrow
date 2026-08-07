package com.safeguard.blocker

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class LightweightBlockerService : AccessibilityService() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var lastSettingsBlockMs = 0L
    @Volatile private var lastContentBlockMs = 0L
    @Volatile private var lastPanicFireMs = 0L
    private val detectionTimes = ArrayList<Long>()
    private val lastBlockByKeyword = HashMap<String, Long>()
    private var keywordsToken = 0L
    private var cachedKeywords = emptyList<String>()
    private val emptyKeywords = emptyList<String>()
    @Volatile private var lastBrowserScanMs = 0L
    @Volatile private var lastUninstallBlockMs = 0L
    @Volatile private var lastScanHash = 0
    @Volatile private var serviceEnabledAtMs = 0L
    private lateinit var recentsSwiper: RecentsSwiper
    private val browserPackages = setOf(
        "com.android.chrome", "org.chromium.chrome",
        "org.mozilla.firefox", "org.mozilla.fennec_fdroid",
        "com.microsoft.emmx", "com.opera.browser",
        "com.opera.mini.native", "com.brave.browser",
        "com.duckduckgo.mobile.android", "com.vivaldi.browser",
        "com.UCMobile.intl", "samsung.android.app.sbrowser",
        "com.microsoft.bing", "com.google.android.apps.chrome",
        "com.chrome.canary", "com.chrome.beta", "com.chrome.dev",
        "com.android.browser", "com.microsoft.edge", "com.sec.android.app.sbrowser",
        "com.kiwibrowser.browser", "mark.via", "org.mozilla.firefox_beta"
    )

    override fun onCreate() {
        super.onCreate()
        bootstrapKeywordsIfNeeded()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceEnabledAtMs = System.currentTimeMillis()
        recentsSwiper = RecentsSwiper(this)
    }

    private fun bootstrapKeywordsIfNeeded() {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        if (!prefs.contains("list")) {
            val edit = prefs.edit()
            edit.putStringSet("list", defaultSet())
            edit.putLong("__token", 1L)
            edit.apply()
            keywordsToken = 1L
            cachedKeywords = defaultSet().toList().sorted()
        }
    }

    private fun currentKeywords(): List<String> {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        val token = prefs.getLong("__token", -1L)
        if (token != keywordsToken) {
            keywordsToken = token
            val set = prefs.getStringSet("list", null)
            cachedKeywords = if (set == null || set.isEmpty()) {
                emptyKeywords
            } else {
                set.toList().sorted()
            }
        }
        return cachedKeywords
    }

    private fun touchKeywordsPrefs() {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        val newToken = prefs.getLong("__token", 0L) + 1
        prefs.edit().putLong("__token", newToken).apply()
    }

    private fun addKeywordInternal(kw: String) {
        val normalized = KeywordNormalizer.normalize(kw)
        if (normalized.isNotBlank()) {
            val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
            val set = (prefs.getStringSet("list", null) ?: defaultSet()).toMutableSet()
            if (set.add(normalized)) {
                prefs.edit().putStringSet("list", set).apply()
                touchKeywordsPrefs()
            }
        }
    }

    private fun removeKeywordInternal(kw: String) {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        val set = (prefs.getStringSet("list", null) ?: defaultSet()).toMutableSet()
        if (set.remove(KeywordNormalizer.normalize(kw))) {
            prefs.edit().putStringSet("list", set).apply()
            touchKeywordsPrefs()
        }
    }

    private fun defaultSet(): Set<String> = setOf(
        "xhamster", "pornhub", "xnxx", "xvideos", "redtube",
        "youporn", "porn", "adultcontent", "xxx", "sex",
        "hentai", "onlyfans", "stripchat", "chaturbate"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""

        if (pkg == packageName) return
        if (pkg.isEmpty()) return

        // Recents/Overview opened? Remove the blocked app's task card.
        // The self-package guard above is the §6 loop protection: the swipe
        // must never react to events we produce ourselves.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            OemDetector.mayBeRecents(pkg, event.className?.toString())
        ) {
            if (::recentsSwiper.isInitialized) recentsSwiper.startIfRecents(this)
            return
        }

        val passwordSet = PasswordManager.isSet(this)
        val source = event.source

        try {
            if (passwordSet && !AppSessionState.isValid() && interceptUninstallRemoval(pkg, source, event)) {
                return
            }

            if (passwordSet && !AppSessionState.isValid() && isSensitivePackage(pkg)) {
                val now = System.currentTimeMillis()
                if (now - lastSettingsBlockMs >= SETTINGS_BLOCK_COOLDOWN_MS) {
                    var mentionsUs = false
                    val txtAll = (event.text?.joinToString(" ") ?: "").lowercase()
                    if (mentionsUs(txtAll)) mentionsUs = true
                    if (!mentionsUs && source != null) {
                        val combined = gatherNodeText(source, 10)
                        if (mentionsUs(combined)) mentionsUs = true
                    }
                    if (mentionsUs) {
                        lastSettingsBlockMs = now
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        handler.postDelayed({
                            if (!AppSessionState.isValid()) {
                                val i = Intent(this, PasswordUnlockActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    putExtra("source", "settings_block")
                                }
                                startActivity(i)
                            }
                        }, 150)
                        return
                    }
                }
            }

            val kws = currentKeywords()
            if (kws.isEmpty()) return

            val isBrowser = pkg in browserPackages
            val etype = event.eventType

            if (isBrowser) {
                if (etype == AccessibilityEvent.TYPE_VIEW_FOCUSED) return
                // Typed URL/address-bar text is the strongest signal of a bad
                // habit — scan it immediately instead of skipping it.
                val urlTyping = etype == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                if (!urlTyping) {
                    val now = System.currentTimeMillis()
                    if (now - lastBrowserScanMs < BROWSER_SCAN_COOLDOWN_MS) return
                    lastBrowserScanMs = now
                }
            }

            var combinedText = event.text?.joinToString(" ") ?: ""
            if (source != null) {
                val nodeText = gatherNodeText(source, if (isBrowser) 10 else 22)
                if (nodeText.isNotEmpty()) {
                    combinedText = if (combinedText.isEmpty()) nodeText else "$combinedText $nodeText"
                }
            }
            if (combinedText.isEmpty()) return
            val normalizedText = KeywordNormalizer.normalize(combinedText)
            if (normalizedText.isEmpty()) return

            val h = normalizedText.hashCode()
            if (h == lastScanHash) return
            lastScanHash = h

            val typed = etype == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            for (kw in kws) {
                if (keywordHits(normalizedText, kw, typed)) {
                    triggerBlock(kw, pkg)
                    return
                }
            }
        } finally {
            runCatching { source?.recycle() }
        }
    }

    private fun isSensitivePackage(pkg: String): Boolean =
        pkg.startsWith("com.android.settings") ||
            pkg == "com.google.android.packageinstaller" ||
            pkg == "com.android.packageinstaller" ||
            pkg == "com.android.vending" ||
            pkg == "com.miui.securitycenter" ||
            pkg == "com.samsung.android.packageinstaller" ||
            pkg == "com.oplus.appdetail" ||
            pkg == "com.coloros.filemanager" ||
            pkg == "com.vivo.appstore"

    private fun mentionsUs(text: String): Boolean =
        text.contains("tengrow") || text.contains(packageName.lowercase())

    /**
     * Whole-word keyword match with a false-positive guard.
     *
     * Generic single words ("sex", "xxx", "porn", ...) only fire when the
     * event is the USER TYPING (search bar, address bar, chat input) — so a
     * normal conversation/response that merely contains the word in a large
     * text blob never triggers a block. Specific names (pornhub, xnxx, ...)
     * and multi-word phrases always match.
     */
    private fun keywordHits(text: String, kw: String, typed: Boolean): Boolean {
        if (kw in WEAK_SEARCH_ONLY && !typed) return false
        return if (kw.indexOf(' ') >= 0) {
            text.contains(kw)
        } else {
            text.matchesWord(kw)
        }
    }

    private fun String.matchesWord(kw: String): Boolean {
        val q = java.util.regex.Pattern.quote(kw)
        return contains(Regex("(^|[^a-z0-9])$q($|[^a-z0-9])"))
    }

    private fun gatherNodeText(node: AccessibilityNodeInfo, maxDepth: Int, depth: Int = 0): String {
        if (depth > maxDepth) return ""
        val sb = StringBuilder(64)
        val t = node.text?.toString()
        val d = node.contentDescription?.toString()
        if (!t.isNullOrEmpty()) { sb.append(' '); sb.append(t.lowercase()) }
        if (!d.isNullOrEmpty()) { sb.append(' '); sb.append(d.lowercase()) }
        val count = node.childCount
        for (i in 0 until count) {
            val c = node.getChild(i) ?: continue
            try {
                sb.append(gatherNodeText(c, maxDepth, depth + 1))
            } catch (_: Exception) {
            } finally {
                runCatching { c.recycle() }
            }
            if (sb.length > 4000) break
        }
        return sb.toString()
    }

    /**
     * Strict uninstall protection: presses Cancel on any removal dialog that
     * mentions this app, bounces the user home and requires a master-password
     * session. Returns true when the screen has been handled.
     */
    private fun interceptUninstallRemoval(
        pkg: String,
        source: AccessibilityNodeInfo?,
        event: AccessibilityEvent
    ): Boolean {
        if (!UninstallProtection.isEnabled(this)) return false
        if (!UninstallProtection.isRemovalScreen(pkg)) return false
        val now = System.currentTimeMillis()
        if (now - lastUninstallBlockMs < UNINSTALL_BLOCK_COOLDOWN_MS) return true

        val text = StringBuilder(event.text?.joinToString(" ") ?: "")
        if (source != null) {
            val nodeText = gatherNodeText(source, 12)
            if (nodeText.isNotEmpty()) text.append(' ').append(nodeText)
        }
        val t = text.toString()
        if (!UninstallProtection.mentionsOurApp(t, this)) return false
        if (!UninstallProtection.mentionsRemoval(t)) return false

        lastUninstallBlockMs = now
        cancelRemovalDialog(source)
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            if (!AppSessionState.isValid()) {
                val i = Intent(this, PasswordUnlockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("source", "uninstall_block")
                }
                runCatching { startActivity(i) }
            }
        }, 150)
        return true
    }

    /** Finds and presses the "Cancel/Keep" button that defeats the removal. */
    private fun cancelRemovalDialog(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val label = node.text?.toString()
        if (label != null && UninstallProtection.isCancelControl(label)) {
            if (runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)) {
                return true
            }
        }
        val count = node.childCount
        for (i in 0 until count) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            if (cancelRemovalDialog(child)) {
                runCatching { child.recycle() }
                return true
            }
            runCatching { child.recycle() }
        }
        return false
    }

    private fun triggerBlock(kw: String, blockedPackage: String) {
        val now = System.currentTimeMillis()
        // Queue this package so its card can be swiped out of Recents when the
        // user opens the overview screen (§1). Entries expire after 30s.
        RecentsCleaner.recordBlock(this, blockedPackage)
        val lastForKeyword = lastBlockByKeyword[kw] ?: 0L
        if (now - lastForKeyword < KEYWORD_REPEAT_COOLDOWN_MS) return
        lastBlockByKeyword[kw] = now
        if (lastBlockByKeyword.size > 256) lastBlockByKeyword.clear()
        if (now - lastContentBlockMs < CONTENT_BLOCK_COOLDOWN_MS) return
        lastContentBlockMs = now
        runCatching {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("", ""))
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
        // Passive shame alert: red line + cursive, shameful message telling the
        // user they are trying to involve themselves in bad habits.
        handler.postDelayed({
            runCatching { ShameAlertActivity.show(this) }
        }, 160)
        trackDetection(now)
    }

    private fun trackDetection(now: Long) {
        if (!PanicConfig.isEnabled(this)) return
        if (now - lastPanicFireMs < PANIC_COOLDOWN_MS) return
        detectionTimes.add(now)
        val windowMs = PanicConfig.windowMs(this)
        while (detectionTimes.isNotEmpty() && now - detectionTimes.first() > windowMs) {
            detectionTimes.removeAt(0)
        }
        if (detectionTimes.size >= PanicConfig.triggerCount(this)) {
            lastPanicFireMs = now
            detectionTimes.clear()
            // Auto cool-down: multiple addiction detections in the window
            // immediately start the strict, persistent 5-minute timer.
            PanicLockdown.start(this, PanicConfig.lockdownMs(this))
            runCatching {
                val i = Intent(this, PanicAlertActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(i)
            }
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        // Aggressive disable detection: the user turned the blocker off in
        // Accessibility settings. Unless the owner has an active unlocked
        // session, the strict cool-down timer starts immediately.
        val now = System.currentTimeMillis()
        if (now - serviceEnabledAtMs > MIN_ENABLED_BEFORE_GUARD_MS &&
            !AppSessionState.isValid()
        ) {
            PanicLockdown.start(this, PanicConfig.lockdownMs(this))
            runCatching {
                val i = Intent(this, PanicAlertActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(i)
            }
        }
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    fun addKeyword(kw: String) = addKeywordInternal(kw)
    fun removeKeyword(kw: String) = removeKeywordInternal(kw)
    fun getKeywords(): List<String> = currentKeywords().toList()

    companion object {
        private const val SETTINGS_BLOCK_COOLDOWN_MS = 1000L
        private const val CONTENT_BLOCK_COOLDOWN_MS = 700L
        private const val BROWSER_SCAN_COOLDOWN_MS = 1800L
        private const val UNINSTALL_BLOCK_COOLDOWN_MS = 900L
        private const val PANIC_COOLDOWN_MS = 60_000L
        private const val KEYWORD_REPEAT_COOLDOWN_MS = 3 * 60_000L
        private const val MIN_ENABLED_BEFORE_GUARD_MS = 60_000L

        /** Generic words that only block while the user is actively typing. */
        private val WEAK_SEARCH_ONLY = setOf("sex", "xxx", "porn", "adult", "adultcontent")
    }
}
