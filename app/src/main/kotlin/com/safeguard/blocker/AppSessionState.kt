package com.safeguard.blocker

object AppSessionState {
    private var unlocked = false
    private var expiry = 0L
    private const val DURATION_MS = 60_000L

    fun unlock() {
        unlocked = true
        expiry = System.currentTimeMillis() + DURATION_MS
    }

    fun lock() {
        unlocked = false
        expiry = 0L
    }

    fun isValid(): Boolean {
        if (!unlocked) return false
        if (System.currentTimeMillis() >= expiry) {
            lock()
            return false
        }
        return true
    }
}
