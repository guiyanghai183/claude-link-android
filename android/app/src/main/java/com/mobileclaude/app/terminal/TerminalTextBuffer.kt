package com.mobileclaude.app.terminal

/**
 * Converts a streaming PTY transcript into bounded plain text for Compose.
 * It intentionally handles shell-oriented ANSI control sequences rather than
 * emulating a full-screen VT terminal such as vim or top.
 */
class TerminalTextBuffer(private val maxChars: Int = 120_000) {
    private enum class ParserState { TEXT, ESCAPE, CSI, OSC, OSC_ESCAPE }

    private val text = StringBuilder()
    private val control = StringBuilder()
    private var state = ParserState.TEXT
    private var carriageReturnPending = false

    fun append(chunk: String): String {
        chunk.forEach(::accept)
        trimToLimit()
        return text.toString()
    }

    fun clear() {
        text.clear()
        control.clear()
        state = ParserState.TEXT
        carriageReturnPending = false
    }

    fun snapshot(): String = text.toString()

    private fun accept(char: Char) {
        when (state) {
            ParserState.TEXT -> acceptText(char)
            ParserState.ESCAPE -> when (char) {
                '[' -> {
                    control.clear()
                    state = ParserState.CSI
                }
                ']' -> state = ParserState.OSC
                else -> state = ParserState.TEXT
            }
            ParserState.CSI -> {
                if (char in '@'..'~') {
                    applyCsi(char, control.toString())
                    control.clear()
                    state = ParserState.TEXT
                } else if (control.length < 64) {
                    control.append(char)
                }
            }
            ParserState.OSC -> when (char) {
                '\u0007' -> state = ParserState.TEXT
                '\u001B' -> state = ParserState.OSC_ESCAPE
            }
            ParserState.OSC_ESCAPE -> state = if (char == '\\') ParserState.TEXT else ParserState.OSC
        }
    }

    private fun acceptText(char: Char) {
        if (carriageReturnPending) {
            if (char == '\n') {
                text.append('\n')
                carriageReturnPending = false
                return
            }
            eraseCurrentLine()
            carriageReturnPending = false
        }
        when (char) {
            '\u001B' -> state = ParserState.ESCAPE
            '\r' -> carriageReturnPending = true
            '\n', '\t' -> text.append(char)
            '\b' -> if (text.isNotEmpty() && text.last() != '\n') text.deleteCharAt(text.lastIndex)
            else -> if (char >= ' ' && char != '\u007F') text.append(char)
        }
    }

    private fun applyCsi(command: Char, parameters: String) {
        when (command) {
            'J' -> if (parameters.split(';').any { it == "2" || it == "3" }) text.clear()
            'K' -> eraseCurrentLine()
        }
    }

    private fun eraseCurrentLine() {
        val start = text.lastIndexOf("\n").let { if (it < 0) 0 else it + 1 }
        text.delete(start, text.length)
    }

    private fun trimToLimit() {
        if (text.length <= maxChars) return
        val overflow = text.length - maxChars
        val newline = text.indexOf("\n", (overflow - 1).coerceAtLeast(0))
        text.delete(0, if (newline >= 0) newline + 1 else overflow)
    }
}
