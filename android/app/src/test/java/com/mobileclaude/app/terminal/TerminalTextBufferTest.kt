package com.mobileclaude.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTextBufferTest {
    @Test
    fun stripsAnsiColorAndOscTitleAcrossChunks() {
        val buffer = TerminalTextBuffer()
        buffer.append("plain \u001B[31")
        buffer.append("mred\u001B[0m\u001B]0;title")
        assertEquals("plain red text", buffer.append("\u0007 text"))
    }

    @Test
    fun carriageReturnOverwritesTheCurrentLineAndCrLfAddsOneNewline() {
        val buffer = TerminalTextBuffer()
        buffer.append("progress 10%\r")
        buffer.append("progress 90%\r\n")
        assertEquals("progress 90%\n", buffer.snapshot())
    }

    @Test
    fun handlesBackspaceEraseLineAndClearScreen() {
        val buffer = TerminalTextBuffer()
        buffer.append("abc\bD\nremove me\u001B[2Kkept")
        assertEquals("abD\nkept", buffer.snapshot())
        assertEquals("fresh", buffer.append("\u001B[2Jfresh"))
    }

    @Test
    fun boundsTranscriptAtLineBoundary() {
        val buffer = TerminalTextBuffer(maxChars = 12)
        assertEquals("second\nthird", buffer.append("first\nsecond\nthird"))
    }
}
