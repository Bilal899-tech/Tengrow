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
                    val i = Intent(this, PasswordUnlockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("source", "settings_block")
                    }
                    startActivity(i)
                    return
                }
            }
        }

        val text = event.text.joinToString(" ").lowercase()
        if (text.isNotEmpty()) {
            for (kw in blockedKeywords) {
                if (text.contains(kw)) {
                    triggerBlock()
                    return
                }
            }
        }

        event.source?.let { root ->
            try {
                scanRecursive(root)
            } finally {
                root.recycle()
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

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                try {
                    if (scanRecursive(child)) return true
                } finally {
                    child.recycle()
                }
            }
        }
        return false
    }

    private fun triggerBlock() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("", ""))
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {}

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
