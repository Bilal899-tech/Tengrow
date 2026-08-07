package com.safeguard.blocker

import android.os.Build

/**
 * Maps [Build.MANUFACTURER] to the concrete [OemRecentsStrategy] that knows
 * how to detect (and swipe away cards in) that OEM's Recents screen.
 *
 * The package/class patterns themselves live inside each strategy; this
 * utility only decides WHICH strategy applies on this device.
 */
object OemDetector {

    /**
     * Picks the strategy for the running device.
     *
     * ORDER MATTERS — keep the most specific brands first; "poco" and "redmi"
     * are Xiaomi sub-brands, "sec" is an old Samsung prefix, "nothing" is
     * Nothing (near-stock), etc.
     */
    fun strategyFor(manufacturer: String?): OemRecentsStrategy {
        val m = manufacturer?.lowercase()?.trim().orEmpty()

        // OEM-FRAGILE: Samsung reports "samsung", "samsung electronics" or
        // older builds "sec". Do not move below the Xiaomi check.
        if (m.contains("samsung") || m.contains("sec")) return SamsungStrategy()

        // OEM-FRAGILE: Xiaomi reports "Xiaomi"/"Redmi"/"POCO"/"Mi". "mi" is
        // deliberately last because plain "mi" can appear in other brands'
        // model names only via Build.MANUFACTURER, which they do not set.
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            return XiaomiStrategy()
        }

        // OEM-FRAGILE: near-stock devices ship the AOSP package layout
        // (com.android.systemui + "*Recents*" class) — Pixel, OnePlus, Motorola,
        // Nokia, Sony, ASUS, Nothing, Oppo/OnePlus, Vivo (Funtouch uses its own
        // launcher but keeps systemui recents), Huawei/Honor (EMUI recents also
        // lives in com.android.systemui), ZTE, Lenovo, LG.
        if (m.contains("google") || m.contains("pixel") || m.contains("oneplus") ||
            m.contains("motorola") || m.contains("nokia") || m.contains("sony") ||
            m.contains("asus") || m.contains("nothing") || m.contains("oppo") ||
            m.contains("vivo") || m.contains("huawei") || m.contains("honor") ||
            m.contains("zte") || m.contains("lenovo") || m.contains("lg")
        ) {
            return StockAndroidStrategy()
        }

        // OEM-FRAGILE: nothing matched — never guess; the generic fallback
        // only acts on unmistakable class tokens (see GenericFallbackStrategy).
        return GenericFallbackStrategy()
    }

    /**
     * Cheap pre-check used by the accessibility service: could this
     * TYPE_WINDOW_STATE_CHANGED event be the system Recents screen?
     */
    fun mayBeRecents(pkg: String?, className: String?): Boolean {
        val preferred = strategyFor(Build.MANUFACTURER)
        if (preferred.isRecentsWindow(pkg, className)) return true

        // OEM-FRAGILE: last resort for mis-detected manufacturers — if the
        // window class *unambiguously* reads as an overview surface, treat it
        // as recents regardless of the detected brand.
        return GenericFallbackStrategy().isRecentsWindow(pkg, className)
    }
}