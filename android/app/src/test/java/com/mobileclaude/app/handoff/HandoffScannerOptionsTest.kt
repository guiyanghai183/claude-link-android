package com.mobileclaude.app.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffScannerOptionsTest {
    @Test
    fun scannerUsesCaptureActivityWithExplicitBackControl() {
        val options = claudeLinkHandoffScanOptions()

        assertEquals(HandoffCaptureActivity::class.java, options.captureActivity)
        assertTrue(com.journeyapps.barcodescanner.CaptureActivity::class.java.isAssignableFrom(options.captureActivity))
    }
}
