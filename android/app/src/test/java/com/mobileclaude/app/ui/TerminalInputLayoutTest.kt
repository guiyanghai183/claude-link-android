package com.mobileclaude.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalInputLayoutTest {
    @Test
    fun terminalInputKeepsMaterialMinimumHeight() {
        assertTrue(TERMINAL_INPUT_MIN_HEIGHT_DP >= 56)
    }
}
