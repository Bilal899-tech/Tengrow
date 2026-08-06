package com.safeguard.blocker

import android.content.Context

/**
 * Strict uninstall protection.
 *
 * When the user has personally granted accessibility + device admin, this
 * makes removing Tengrow from ANYWHERE (Home screen long-press, Settings,
 * Play Store, widgets) almost impossible while the device is locked:
 *   - removal/disable dialogs that mention this app are detected,
 *   - the Cancel button is pressed automatically,
 *   - the user is bounced to the home screen and a master-password session
 *     is requested.
 *
 * Own flows stay intact: changing the master password and deactivating the
 * device admin keep working with a valid unlocked session.
 */
object UninstallProtection {

    private const val PREFS = "uninstall_protection"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Foreground packages that can open an uninstall/disable dialog. */
    fun isRemovalScreen(pkg: String): Boolean =
        pkg == "com.android.packageinstaller" ||
            pkg == "com.google.android.packageinstaller" ||
            pkg == "com.android.settings" ||
            pkg == "com.miui.packageinstaller" ||
            pkg == "com.samsung.android.packageinstaller" ||
            pkg == "com.vivo.appstore" ||
            pkg == "com.oplus.appdetail" ||
            pkg == "com.coloros.filemanager"

    /** Does the visible text look like an uninstall/disable/delete request? */
    fun mentionsRemoval(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("uninstall") ||
            t.contains("remove") ||
            t.contains("delete") ||
            t.contains("disable") ||
            t.contains("deactivate") ||
            t.contains("erase") ||
            t.contains("turn off this service")
    }

    /** Does the visible text mention our own app (label or package name)? */
    fun mentionsOurApp(text: String, ctx: Context): Boolean {
        val t = text.lowercase()
        if (t.contains(ctx.packageName.lowercase())) return true
        val label = runCatching {
            ctx.applicationInfo.loadLabel(ctx.packageManager).toString()
        }.getOrDefault("tengrow")
        return label.isNotBlank() && t.contains(label.lowercase())
    }

    /** Is this control the "keep the app" (cancel) side of the dialog? */
    fun isCancelControl(text: String): Boolean {
        val s = text.trim().lowercase()
            .replace(Regex("\\s+"), " ")
        if (s.isBlank()) return false
        return s in CANCEL_LABELS
    }

    private val CANCEL_LABELS = setOf(
        "cancel", "close", "keep", "keep app", "keep this app",
        "no", "nope", "not now", "don't", "don't remove",
        "don't disable", "turn off disable", "stay", "undo"
    )
}