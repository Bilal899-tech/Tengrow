package com.safeguard.blocker

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class LightweightBlockerService : AccessibilityService() {

    private val blockedKeywords = mutableListOf<String>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        reloadKeywords()
    }

    private fun reloadKeywords() {
        blockedKeywords.clear()
        blockedKeywords.addAll(loadKeywords())
    }

    private fun loadKeywords(): List<String> {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        return prefs.getStringSet("list", defaultSet())?.toList()?.sorted() ?: defaultSet().toList()
    }

    private fun defaultSet(): Set<String> = setOf(
        "xhamster", "pornhub", "xnxx", "xvideos", "redtube",
        "youporn", "porn", "adultcontent", "xxx", "sex",
        "hentai", "onlyfans", "stripchat", "chaturbate"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""
        val cls = event.className?.toString() ?: ""

        if (pkg == "com.android.settings") {
            if (cls.contains("InstalledAppDetails") ||
                cls.contains("ApplicationInfo") ||
                cls.contains("ManageApplications") ||
                cls.contains("AccessibilitySettings") ||
                cls.contains("DeviceAdminSettings") ||
                cls.contains("SecuritySettings")
            ) {
                if (!AppSessionState.isValid()) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    handler.postDelayed({
                        val i = Intent(this, PasswordUnlockActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("source", "settings_block")
                        }
                        startActivity(i)
                    }, 250)
                    return
                }
            }
        }

        val text = event.text?.joinToString(" ")?.lowercase() ?: ""
        if (text.isNotEmpty()) {
            for (kw in blockedKeywords) {
                if (text.contains(kw)) {
                    triggerBlock()
                    return
                }
            }
        }

        val root = event.source
        if (root != null) {
            try {
                scanRecursive(root)
            } catch (_: Exception) {
            } finally {
                runCatching { root.recycle() }
            }
        }
    }

    private fun scanRecursive(node: AccessibilityNodeInfo): Boolean {
        val txt = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val id = node.viewIdResourceName?.lowercase() ?: ""

        for (kw in blockedKeywords) {
            if (txt.contains(kw) || desc.contains(kw) || id.contains(kw)) {
                triggerBlock()
                return true
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            try {
                if (scanRecursive(child)) return true
            } catch (_: Exception) {
            } finally {
                runCatching { child.recycle() }
            }
        }
        return false
    }

    private fun triggerBlock() {
        runCatching {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("", ""))
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 100)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    fun addKeyword(kw: String) {
        val lower = kw.lowercase()
        if (lower.isNotBlank()) {
            val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
            val set = prefs.getStringSet("list", defaultSet())?.toMutableSet() ?: defaultSet().toMutableSet()
            set.add(lower)
            prefs.edit().putStringSet("list", set).apply()
            reloadKeywords()
        }
    }

    fun removeKeyword(kw: String) {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("list", defaultSet())?.toMutableSet() ?: defaultSet().toMutableSet()
        set.remove(kw.lowercase())
        prefs.edit().putStringSet("list", set).apply()
        reloadKeywords()
    }

    fun getKeywords(): List<String> = blockedKeywords.toList()
}
