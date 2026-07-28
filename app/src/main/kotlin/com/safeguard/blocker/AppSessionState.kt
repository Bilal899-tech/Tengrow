package com.safeguard.blocker

object AppSessionState {
    private var unlocked = false
    private var expiry = 0L
    private const val DURATION_MS = 60_000L

    @Synchronized
    fun unlock() {
        unlocked = true
        expiry = System.currentTimeMillis() + DURATION_MS
    }

    @Synchronized
    fun tempUnlock(durationMs: Long = 15_000L) {
        unlocked = true
        val now = System.currentTimeMillis()
        if (expiry < now + durationMs) {
            expiry = now + durationMs
        }
    }

    @Synchronized
    fun lock() {
        unlocked = false
        expiry = 0L
    }

    @Synchronized
    fun isValid(): Boolean {
        if (!unlocked) return false
        if (System.currentTimeMillis() >= expiry) {
            unlocked = false
            expiry = 0L
            return false
        }
        return true
    }
}
