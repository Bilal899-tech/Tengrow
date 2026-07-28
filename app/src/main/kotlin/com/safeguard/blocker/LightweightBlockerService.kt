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
    private var keywordsToken = 0L
    private var cachedKeywords = emptyList<String>()
    private val emptyKeywords = emptyList<String>()
    @Volatile private var lastBrowserScanMs = 0L
    @Volatile private var lastScanHash = 0
    private val browserPackages = setOf(
        "com.android.chrome", "org.chromium.chrome",
        "org.mozilla.firefox", "org.mozilla.fennec_fdroid",
        "com.microsoft.emmx", "com.opera.browser",
        "com.opera.mini.native", "com.brave.browser",
        "com.duckduckgo.mobile.android", "com.vivaldi.browser",
        "com.UCMobile.intl", "samsung.android.app.sbrowser",
        "com.microsoft.bing", "com.google.android.apps.chrome",
        "com.chrome.canary", "com.chrome.beta", "com.chrome.dev"
    )

    override fun onCreate() {
        super.onCreate()
        bootstrapKeywordsIfNeeded()
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
        val lower = kw.lowercase()
        if (lower.isNotBlank()) {
            val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
            val set = (prefs.getStringSet("list", null) ?: defaultSet()).toMutableSet()
            if (set.add(lower)) {
                prefs.edit().putStringSet("list", set).apply()
                touchKeywordsPrefs()
            }
        }
    }

    private fun removeKeywordInternal(kw: String) {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        val set = (prefs.getStringSet("list", null) ?: defaultSet()).toMutableSet()
        if (set.remove(kw.lowercase())) {
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
        val cls = event.className?.toString() ?: ""

        if (pkg == packageName) return
        if (pkg.isEmpty()) return

        if (pkg == "com.android.settings") {
            val c = cls
            if (c.endsWith(".InstalledAppDetails") ||
                c.endsWith(".ApplicationInfo") ||
                c.endsWith(".ManageApplications") ||
                c.endsWith(".AccessibilitySettings") ||
                c.endsWith(".DeviceAdminSettings") ||
                c.endsWith(".SecuritySettings") ||
                c.endsWith(".UninstallerActivity") ||
                c.endsWith(".UninstallActivity") ||
                c.endsWith(".UninstallFragment") ||
                c.endsWith(".ConfirmDeviceAdminRemove") ||
                c.endsWith(".AppDashboardFragmentBase") ||
                c.contains("AppDashboard") ||
                c.contains("Uninstall") ||
                c.contains("RemoveAdmin") ||
                c == "com.android.settings.InstalledAppDetails" ||
                c == "com.android.settings.applications.InstalledAppDetailsTopLevelActivity" ||
                c == "com.android.settings.Settings\$AccessibilitySettingsActivity" ||
                c == "com.android.settings.Settings\$DeviceAdminSettingsActivity" ||
                c == "com.android.settings.Settings\$SecuritySettingsActivity" ||
                c == "com.android.settings.applications.appinfo.AppInfoDashboardFragment" ||
                c == "com.android.settings.Settings\$ManageApplicationsActivity"
            ) {
                if (!AppSessionState.isValid()) {
                    val now = System.currentTimeMillis()
                    if (now - lastSettingsBlockMs < SETTINGS_BLOCK_COOLDOWN_MS) return
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
                    }, 250)
                    return
                }
            }
        }

        if (!AppSessionState.isValid()) {
            val now = System.currentTimeMillis()
            if (now - lastSettingsBlockMs >= SETTINGS_BLOCK_COOLDOWN_MS) {
                val c = cls
                if (pkg.startsWith("com.android.settings") ||
                    pkg == "com.google.android.packageinstaller" ||
                    pkg == "com.android.packageinstaller" ||
                    pkg == "com.android.vending" ||
                    pkg == "com.miui.securitycenter" ||
                    pkg == "com.samsung.android.packageinstaller" ||
                    pkg == "com.oplus.appdetail" ||
                    pkg == "com.coloros.filemanager" ||
                    pkg == "com.vivo.appstore"
                ) {
                    var uninstallForUs = false
                    val txtAll = (event.text?.joinToString(" ") ?: "").lowercase()
                    if (txtAll.contains("tengrow") || txtAll.contains(packageName.lowercase())) {
                        uninstallForUs = true
                    }
                    if (!uninstallForUs) {
                        val root = event.source
                        if (root != null) {
                            try {
                                val combined = gatherNodeText(root, 8)
                                if (combined.contains("tengrow") || combined.contains(packageName.lowercase())) {
                                    uninstallForUs = true
                                }
                            } catch (_: Exception) {
                            } finally {
                                runCatching { root.recycle() }
                            }
                        }
                    }
                    if (uninstallForUs) {
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
                        }, 250)
                        return
                    }
                }
            }
        }

        val kws = currentKeywords()
        if (kws.isEmpty()) return

        val isBrowser = pkg in browserPackages
        val etype = event.eventType

        if (isBrowser) {
            when (etype) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> return
            }
            val now = System.currentTimeMillis()
            if (now - lastBrowserScanMs < BROWSER_SCAN_COOLDOWN_MS) return
            lastBrowserScanMs = now
        }

        var combinedText = event.text?.joinToString(" ")?.lowercase() ?: ""
        val root = event.source
        if (root != null) {
            try {
                val nodeText = gatherNodeText(root, if (isBrowser) 10 else 22)
                if (nodeText.isNotEmpty()) {
                    combinedText = if (combinedText.isEmpty()) nodeText else "$combinedText $nodeText"
                }
            } catch (_: Exception) {
            } finally {
                runCatching { root.recycle() }
            }
        }
        if (combinedText.isEmpty()) return

        val h = combinedText.hashCode()
        if (h == lastScanHash) return
        lastScanHash = h

        for (kw in kws) {
            if (combinedText.contains(kw)) {
                triggerBlock()
                return
            }
        }
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

    private fun triggerBlock() {
        val now = System.currentTimeMillis()
        if (now - lastContentBlockMs < CONTENT_BLOCK_COOLDOWN_MS) return
        lastContentBlockMs = now
        runCatching {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("", ""))
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
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
        private const val SETTINGS_BLOCK_COOLDOWN_MS = 2500L
        private const val CONTENT_BLOCK_COOLDOWN_MS = 700L
        private const val BROWSER_SCAN_COOLDOWN_MS = 1800L
    }
}
