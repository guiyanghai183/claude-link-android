package com.mobileclaude.app.terminal

/**
 * Small VT-style screen buffer for the Codex full-screen CLI.
 *
 * The existing terminal chat intentionally flattens ANSI output. Codex uses cursor movement,
 * alternate-screen switching and line erasure, so its dedicated mobile window needs an actual
 * screen model. This parser implements the control sequences emitted by Codex/tmux while keeping
 * the Android UI independent from a WebView or a network-loaded terminal library.
 */
class CodexTerminalBuffer(
    columns: Int = DEFAULT_COLUMNS,
    rows: Int = DEFAULT_ROWS,
) {
    private var columns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    private var rows = rows.coerceIn(MIN_ROWS, MAX_ROWS)
    private var cells = newScreen(this.columns, this.rows)
    private val scrollback = ArrayDeque<CharArray>()
    private var cursorRow = 0
    private var cursorColumn = 0
    private var savedRow = 0
    private var savedColumn = 0
    private var scrollTop = 0
    private var scrollBottom = this.rows - 1
    private var state = ParserState.NORMAL
    private val sequence = StringBuilder()
    private var pendingHighSurrogate: Char? = null

    @Synchronized
    fun append(chunk: String): String {
        var index = 0
        pendingHighSurrogate?.let { high ->
            if (chunk.firstOrNull()?.isLowSurrogate() == true) {
                putCodePoint(Character.toCodePoint(high, chunk[0]))
                index = 1
            } else {
                putCodePoint(high.code)
            }
            pendingHighSurrogate = null
        }
        while (index < chunk.length) {
            val char = chunk[index]
            if (state == ParserState.NORMAL && char.isHighSurrogate()) {
                if (index + 1 < chunk.length && chunk[index + 1].isLowSurrogate()) {
                    putCodePoint(Character.toCodePoint(char, chunk[index + 1]))
                    index += 2
                    continue
                }
                pendingHighSurrogate = char
                break
            }
            consume(char)
            index += 1
        }
        return render()
    }

    @Synchronized
    fun resize(newColumns: Int, newRows: Int): String {
        val width = newColumns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val height = newRows.coerceIn(MIN_ROWS, MAX_ROWS)
        if (width == columns && height == rows) return render()
        val replacement = newScreen(width, height)
        val copiedRows = minOf(rows, height)
        val copiedColumns = minOf(columns, width)
        for (row in 0 until copiedRows) {
            cells[row].copyInto(replacement[row], endIndex = copiedColumns)
        }
        columns = width
        rows = height
        cells = replacement
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorColumn = cursorColumn.coerceIn(0, columns - 1)
        scrollTop = 0
        scrollBottom = rows - 1
        return render()
    }

    @Synchronized
    fun clear(): String {
        cells = newScreen(columns, rows)
        scrollback.clear()
        cursorRow = 0
        cursorColumn = 0
        savedRow = 0
        savedColumn = 0
        scrollTop = 0
        scrollBottom = rows - 1
        state = ParserState.NORMAL
        sequence.clear()
        pendingHighSurrogate = null
        return render()
    }

    @Synchronized
    fun render(): String = buildString {
        scrollback.forEach { row ->
            append(renderRow(row))
            append('\n')
        }
        cells.forEach { row ->
            append(renderRow(row))
            append('\n')
        }
    }.trimEnd('\n')

    private fun consume(char: Char) {
        when (state) {
            ParserState.NORMAL -> consumeNormal(char)
            ParserState.ESCAPE -> consumeEscape(char)
            ParserState.ESCAPE_INTERMEDIATE -> consumeEscapeIntermediate(char)
            ParserState.CSI -> consumeCsi(char)
            ParserState.OSC -> consumeOsc(char)
            ParserState.OSC_ESCAPE -> {
                state = if (char == '\\') ParserState.NORMAL else ParserState.OSC
            }
        }
    }

    private fun consumeNormal(char: Char) {
        when (char) {
            '\u001b' -> state = ParserState.ESCAPE
            '\u009b' -> {
                sequence.clear()
                state = ParserState.CSI
            }
            '\u009d' -> {
                sequence.clear()
                state = ParserState.OSC
            }
            '\r' -> cursorColumn = 0
            '\n', '\u000b', '\u000c' -> lineFeed()
            '\b' -> cursorColumn = (cursorColumn - 1).coerceAtLeast(0)
            '\t' -> cursorColumn = (((cursorColumn / 8) + 1) * 8).coerceAtMost(columns - 1)
            '\u0007', '\u0000', '\u000e', '\u000f' -> Unit
            else -> if (char.code >= 0x20 && char.code != 0x7f) putCodePoint(char.code)
        }
    }

    private fun consumeEscape(char: Char) {
        when (char) {
            '[' -> {
                sequence.clear()
                state = ParserState.CSI
            }
            ']' -> {
                sequence.clear()
                state = ParserState.OSC
            }
            '7' -> {
                saveCursor()
                state = ParserState.NORMAL
            }
            '8' -> {
                restoreCursor()
                state = ParserState.NORMAL
            }
            'D' -> {
                lineFeed()
                state = ParserState.NORMAL
            }
            'M' -> {
                reverseIndex()
                state = ParserState.NORMAL
            }
            'E' -> {
                cursorColumn = 0
                lineFeed()
                state = ParserState.NORMAL
            }
            'c' -> {
                clear()
                state = ParserState.NORMAL
            }
            else -> state = if (char.code in 0x20..0x2f) {
                ParserState.ESCAPE_INTERMEDIATE
            } else {
                ParserState.NORMAL
            }
        }
    }

    /** Consume sequences such as ESC ( B, which selects the ASCII character set. */
    private fun consumeEscapeIntermediate(char: Char) {
        if (char.code in 0x30..0x7e) state = ParserState.NORMAL
    }

    private fun consumeCsi(char: Char) {
        if (char.code in 0x40..0x7e) {
            applyCsi(char, sequence.toString())
            sequence.clear()
            state = ParserState.NORMAL
        } else if (sequence.length < MAX_SEQUENCE_CHARS) {
            sequence.append(char)
        } else {
            sequence.clear()
            state = ParserState.NORMAL
        }
    }

    private fun consumeOsc(char: Char) {
        when (char) {
            '\u0007' -> state = ParserState.NORMAL
            '\u001b' -> state = ParserState.OSC_ESCAPE
            else -> if (sequence.length < MAX_SEQUENCE_CHARS) sequence.append(char)
        }
    }

    private fun applyCsi(command: Char, raw: String) {
        val privateMode = raw.startsWith('?')
        val clean = raw.trimStart('?', '>', '!')
        val parameters = clean.split(';').map { it.toIntOrNull() }
        fun parameter(index: Int, default: Int = 1): Int = parameters.getOrNull(index) ?: default
        when (command) {
            'A' -> cursorRow = (cursorRow - parameter(0)).coerceAtLeast(0)
            'B', 'e' -> cursorRow = (cursorRow + parameter(0)).coerceAtMost(rows - 1)
            'C', 'a' -> cursorColumn = (cursorColumn + parameter(0)).coerceAtMost(columns - 1)
            'D' -> cursorColumn = (cursorColumn - parameter(0)).coerceAtLeast(0)
            'E' -> {
                cursorRow = (cursorRow + parameter(0)).coerceAtMost(rows - 1)
                cursorColumn = 0
            }
            'F' -> {
                cursorRow = (cursorRow - parameter(0)).coerceAtLeast(0)
                cursorColumn = 0
            }
            'G', '`' -> cursorColumn = (parameter(0) - 1).coerceIn(0, columns - 1)
            'H', 'f' -> {
                cursorRow = (parameter(0) - 1).coerceIn(0, rows - 1)
                cursorColumn = (parameter(1) - 1).coerceIn(0, columns - 1)
            }
            'J' -> eraseDisplay(parameter(0, 0))
            'K' -> eraseLine(parameter(0, 0))
            'L' -> insertLines(parameter(0))
            'M' -> deleteLines(parameter(0))
            'P' -> deleteCharacters(parameter(0))
            '@' -> insertCharacters(parameter(0))
            'X' -> eraseCharacters(parameter(0))
            'S' -> repeat(parameter(0)) { scrollUp(scrollTop, scrollBottom) }
            'T' -> repeat(parameter(0)) { scrollDown(scrollTop, scrollBottom) }
            'r' -> {
                val top = (parameter(0) - 1).coerceIn(0, rows - 1)
                val bottom = (parameter(1, rows) - 1).coerceIn(top, rows - 1)
                scrollTop = top
                scrollBottom = bottom
                cursorRow = top
                cursorColumn = 0
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
            'h', 'l' -> {
                if (privateMode && parameters.any { it in setOf(47, 1047, 1049) }) {
                    if (command == 'h') clearScreenOnly()
                    cursorRow = 0
                    cursorColumn = 0
                }
            }
            // SGR, cursor visibility, bracketed paste and device mode controls do not change cells.
            'm', 'n', 'c', 'q', 't' -> Unit
        }
    }

    private fun putCodePoint(codePoint: Int) {
        if (codePoint in COMBINING_RANGES) return
        if (cursorColumn >= columns) {
            cursorColumn = 0
            lineFeed()
        }
        val chars = Character.toChars(codePoint)
        clearCellOccupancy(cursorRow, cursorColumn)
        cells[cursorRow][cursorColumn] = chars[0]
        val width = if (isWide(codePoint)) 2 else 1
        if (width == 2 && cursorColumn + 1 < columns) {
            clearCellOccupancy(cursorRow, cursorColumn + 1)
            cells[cursorRow][cursorColumn + 1] = chars.getOrElse(1) { WIDE_CONTINUATION }
        }
        cursorColumn += width
    }

    private fun clearCellOccupancy(row: Int, column: Int) {
        val line = cells[row]
        if (line[column] == WIDE_CONTINUATION || line[column].isLowSurrogate()) {
            if (column > 0) line[column - 1] = ' '
        }
        if (
            column + 1 < columns &&
            (line[column + 1] == WIDE_CONTINUATION || line[column + 1].isLowSurrogate())
        ) {
            line[column + 1] = ' '
        }
        line[column] = ' '
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) scrollUp(scrollTop, scrollBottom)
        else cursorRow = (cursorRow + 1).coerceAtMost(rows - 1)
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) scrollDown(scrollTop, scrollBottom)
        else cursorRow = (cursorRow - 1).coerceAtLeast(0)
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (row in cursorRow + 1 until rows) cells[row].fill(' ')
            }
            1 -> {
                for (row in 0 until cursorRow) cells[row].fill(' ')
                eraseLine(1)
            }
            2, 3 -> clearScreenOnly()
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> cells[cursorRow].fill(' ', cursorColumn, columns)
            1 -> cells[cursorRow].fill(' ', 0, (cursorColumn + 1).coerceAtMost(columns))
            2 -> cells[cursorRow].fill(' ')
        }
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (row in scrollBottom downTo cursorRow + 1) cells[row - 1].copyInto(cells[row])
            cells[cursorRow].fill(' ')
        }
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (row in cursorRow until scrollBottom) cells[row + 1].copyInto(cells[row])
            cells[scrollBottom].fill(' ')
        }
    }

    private fun deleteCharacters(count: Int) {
        val amount = count.coerceIn(1, columns - cursorColumn)
        val row = cells[cursorRow]
        row.copyInto(row, cursorColumn, cursorColumn + amount, columns)
        row.fill(' ', columns - amount, columns)
    }

    private fun insertCharacters(count: Int) {
        val amount = count.coerceIn(1, columns - cursorColumn)
        val row = cells[cursorRow]
        row.copyInto(row, cursorColumn + amount, cursorColumn, columns - amount)
        row.fill(' ', cursorColumn, cursorColumn + amount)
    }

    private fun eraseCharacters(count: Int) {
        cells[cursorRow].fill(' ', cursorColumn, (cursorColumn + count).coerceAtMost(columns))
    }

    private fun scrollUp(top: Int, bottom: Int) {
        if (top == 0) {
            scrollback.addLast(cells[top].copyOf())
            while (scrollback.size > MAX_SCROLLBACK_ROWS) scrollback.removeFirst()
        }
        for (row in top until bottom) cells[row + 1].copyInto(cells[row])
        cells[bottom].fill(' ')
    }

    private fun scrollDown(top: Int, bottom: Int) {
        for (row in bottom downTo top + 1) cells[row - 1].copyInto(cells[row])
        cells[top].fill(' ')
    }

    private fun saveCursor() {
        savedRow = cursorRow
        savedColumn = cursorColumn
    }

    private fun restoreCursor() {
        cursorRow = savedRow.coerceIn(0, rows - 1)
        cursorColumn = savedColumn.coerceIn(0, columns - 1)
    }

    private fun clearScreenOnly() {
        cells.forEach { it.fill(' ') }
    }

    private fun renderRow(row: CharArray): String = buildString(row.size) {
        row.forEach { cell -> if (cell != WIDE_CONTINUATION) append(cell) }
    }.trimEnd()

    private fun isWide(codePoint: Int): Boolean =
        codePoint in 0x1100..0x115f ||
            codePoint in 0x2329..0x232a ||
            codePoint in 0x2e80..0xa4cf ||
            codePoint in 0xac00..0xd7a3 ||
            codePoint in 0xf900..0xfaff ||
            codePoint in 0xfe10..0xfe19 ||
            codePoint in 0xfe30..0xfe6f ||
            codePoint in 0xff00..0xff60 ||
            codePoint in 0xffe0..0xffe6 ||
            codePoint in 0x1f300..0x1faff ||
            codePoint in 0x20000..0x3fffd

    private enum class ParserState { NORMAL, ESCAPE, ESCAPE_INTERMEDIATE, CSI, OSC, OSC_ESCAPE }

    companion object {
        const val DEFAULT_COLUMNS = 56
        const val DEFAULT_ROWS = 28
        const val MIN_COLUMNS = 32
        const val MAX_COLUMNS = 160
        const val MIN_ROWS = 12
        const val MAX_ROWS = 100
        const val MAX_SCROLLBACK_ROWS = 1_200
        private const val MAX_SEQUENCE_CHARS = 128
        private const val WIDE_CONTINUATION = '\u0000'
        private val COMBINING_RANGES = listOf(
            0x0300..0x036f,
            0x1ab0..0x1aff,
            0x1dc0..0x1dff,
            0x20d0..0x20ff,
            0xfe20..0xfe2f,
        )

        private fun newScreen(columns: Int, rows: Int): Array<CharArray> =
            Array(rows) { CharArray(columns) { ' ' } }

        private operator fun List<IntRange>.contains(value: Int): Boolean = any { value in it }
    }
}
