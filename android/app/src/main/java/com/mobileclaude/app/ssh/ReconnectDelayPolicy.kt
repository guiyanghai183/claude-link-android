package com.mobileclaude.app.ssh

internal object ReconnectDelayPolicy {
    private val delaysMillis = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000, 30_000)

    fun delayMillisAfterFailure(failureCount: Int): Long {
        val index = (failureCount - 1).coerceIn(0, delaysMillis.lastIndex)
        return delaysMillis[index]
    }
}
