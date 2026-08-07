package com.safeguard.blocker

/**
 * The ordered steps of the accessibility-driven uninstall flow (§4).
 *
 * The helper walks this list; every step has its own 5-second timeout and is
 * logged to the encrypted action log.
 */
sealed class AccessibilityAction {
    /** Step 1 — launch the app-details screen for [packageName]. */
    data class OpenAppSettings(val packageName: String) : AccessibilityAction()

    /** Step 2 — tap the "Uninstall"/"Remove"/"Disable app" button. */
    object ClickUninstall : AccessibilityAction()

    /** Step 3 — tap the positive button of the confirmation dialog. */
    object ClickConfirmDialog : AccessibilityAction()
}