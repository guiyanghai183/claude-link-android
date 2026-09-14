package com.mobileclaude.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateStateTest {
    private val update = AppUpdate(
        versionCode = 22,
        versionName = "0.3.15",
        apkUrl = "https://github.com/example/example/releases/download/v0.3.15/app.apk",
        sha256 = "0".repeat(64),
        releaseNotes = "",
    )

    @Test
    fun downloadingReportsDeterminateProgressWhenTotalSizeIsKnown() {
        val state = UpdateState.Downloading(
            update = update,
            downloadedBytes = 25L,
            totalBytes = 100L,
        )

        assertEquals(0.25f, state.progress!!, 0.0001f)
        assertEquals(25, state.percent)
    }

    @Test
    fun downloadingClampsProgressToValidRange() {
        val state = UpdateState.Downloading(
            update = update,
            downloadedBytes = 120L,
            totalBytes = 100L,
        )

        assertEquals(1f, state.progress!!, 0.0001f)
        assertEquals(100, state.percent)
    }

    @Test
    fun downloadingKeepsProgressIndeterminateWhenTotalSizeIsUnknown() {
        val state = UpdateState.Downloading(
            update = update,
            downloadedBytes = 512L,
            totalBytes = null,
        )

        assertNull(state.progress)
        assertNull(state.percent)
    }
}
