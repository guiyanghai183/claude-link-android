package com.mobileclaude.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexTerminalBufferTest {
    @Test
    fun cursorMovementAndEraseRenderFullScreenUpdates() {
        val buffer = CodexTerminalBuffer(columns = 12, rows = 4)
        buffer.append("one\r\ntwo\r\nthree")
        buffer.append("\u001b[2;1H\u001b[2Kupdated")

        assertEquals("one\nupdated\nthree", buffer.render())
    }

    @Test
    fun alternateScreenClearsPreviousShellOutput() {
        val buffer = CodexTerminalBuffer(columns = 12, rows = 4)
        buffer.append("shell prompt")
        buffer.append("\u001b[?1049hCodex")

        assertEquals("Codex", buffer.render())
    }

    @Test
    fun wideChineseCharactersUseTwoTerminalCells() {
        val buffer = CodexTerminalBuffer(columns = 8, rows = 3)
        buffer.append("你好ab")
        buffer.append("\u001b[1;7HZ")

        assertTrue(buffer.render().startsWith("你好abZ"))
    }

    @Test
    fun splitOscAndCsiSequencesAreNotDisplayed() {
        val buffer = CodexTerminalBuffer(columns = 16, rows = 3)
        buffer.append("\u001b]0;Cod")
        buffer.append("ex\u0007ready\u001b[")
        buffer.append("31m!")

        assertEquals("ready!", buffer.render())
    }

    @Test
    fun splitCharsetSelectionDoesNotLeakLetterBIntoScreen() {
        val buffer = CodexTerminalBuffer(columns = 24, rows = 3)
        buffer.append("\u001b(")
        buffer.append("BTip: \u001b)BNew")

        assertEquals("Tip: New", buffer.render())
    }

    @Test
    fun eightBitCsiIsHandledAsControlSequence() {
        val buffer = CodexTerminalBuffer(columns = 16, rows = 3)
        buffer.append("stale")
        buffer.append("\u009b2J\u009bHready")

        assertEquals("ready", buffer.render())
    }

    @Test
    fun supplementaryWideGlyphSurvivesChunkBoundary() {
        val buffer = CodexTerminalBuffer(columns = 8, rows = 3)
        val emoji = "🚀"
        buffer.append(emoji.substring(0, 1))
        buffer.append(emoji.substring(1) + "ok")

        assertEquals("🚀ok", buffer.render())
    }

    @Test
    fun resizePreservesVisibleCells() {
        val buffer = CodexTerminalBuffer(columns = 10, rows = 3)
        buffer.append("alpha\r\nbeta")

        val rendered = buffer.resize(14, 5)

        assertEquals("alpha\nbeta", rendered)
    }
}
