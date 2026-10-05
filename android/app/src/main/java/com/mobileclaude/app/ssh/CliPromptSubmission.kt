package com.mobileclaude.app.ssh

import kotlinx.coroutines.delay
import java.io.IOException

internal const val CLI_PASTE_SETTLE_MILLIS = 200L

/** Ink-based CLIs need a separate input event for Enter after accepting a paste. */
internal suspend fun submitCliPrompt(
    text: String,
    write: (String) -> Unit,
    isCurrent: () -> Boolean = { true },
) {
    val clean = text.replace("\u001b", "")
    require(clean.isNotBlank()) { "消息不能为空" }
    fun checkSession() {
        if (!isCurrent()) throw IOException("窗口已切换或断开，消息尚未提交，草稿已保留")
    }
    checkSession()
    write("\u001b[200~$clean\u001b[201~")
    delay(CLI_PASTE_SETTLE_MILLIS)
    checkSession()
    write("\r")
}
