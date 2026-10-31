package com.example.universalmusic.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes callers and enforces a minimum interval between acquisitions. */
class RateLimiter(
    private val minIntervalMs: Long,
    private val timeSourceMs: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()
    private var lastRequestEpochMs: Long = 0L

    suspend fun acquire() {
        if (minIntervalMs <= 0L) return
        mutex.withLock {
            val waitMs = minIntervalMs - (timeSourceMs() - lastRequestEpochMs)
            if (waitMs > 0L && lastRequestEpochMs != 0L) delay(waitMs)
            lastRequestEpochMs = timeSourceMs()
        }
    }
}
