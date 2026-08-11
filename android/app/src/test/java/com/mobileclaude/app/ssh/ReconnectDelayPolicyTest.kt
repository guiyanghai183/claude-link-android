package com.mobileclaude.app.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectDelayPolicyTest {
    @Test
    fun retryDelayBacksOffAndCapsAtThirtySeconds() {
        assertEquals(1_000L, ReconnectDelayPolicy.delayMillisAfterFailure(1))
        assertEquals(2_000L, ReconnectDelayPolicy.delayMillisAfterFailure(2))
        assertEquals(4_000L, ReconnectDelayPolicy.delayMillisAfterFailure(3))
        assertEquals(8_000L, ReconnectDelayPolicy.delayMillisAfterFailure(4))
        assertEquals(15_000L, ReconnectDelayPolicy.delayMillisAfterFailure(5))
        assertEquals(30_000L, ReconnectDelayPolicy.delayMillisAfterFailure(6))
        assertEquals(30_000L, ReconnectDelayPolicy.delayMillisAfterFailure(20))
    }
}
