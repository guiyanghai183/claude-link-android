package com.mobileclaude.app.handoff

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

data class ClaudeLinkHandoff(
    val source: String,
    val title: String,
    val handoff: String,
    val createdAtEpochSeconds: Long,
    val threadId: String?,
) {
    fun initialPrompt(): String = buildString {
        appendLine("这是从 Claude Link 扫码导入的对话接力。请直接继续未完成的回答或任务，不要从头重做已经完成的调查。")
        appendLine("来源：$source")
        appendLine("标题：$title")
        appendLine()
        append(handoff)
    }
}

object ClaudeLinkHandoffCodec {
    private const val URI_PREFIX = "claudelink://handoff/v1?d="
    private const val MAX_ENCODED_CHARS = 2_600
    private const val MAX_DECOMPRESSED_BYTES = 32_768
    private const val MAX_TITLE_CHARS = 120
    private const val MAX_HANDOFF_CHARS = 5_000
    private val sessionIdPattern = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE,
    )

    fun decode(raw: String): ClaudeLinkHandoff {
        val value = raw.trim()
        require(value.startsWith(URI_PREFIX)) { "不是受支持的 Claude Link 接力二维码" }
        val encoded = value.removePrefix(URI_PREFIX)
        require(encoded.isNotEmpty() && encoded.length <= MAX_ENCODED_CHARS) { "Claude Link 接力载荷长度无效" }
        val compressed = runCatching { Base64.getUrlDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("Claude Link 接力载荷编码无效") }
        val json = inflateBounded(compressed)
        val payload = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("Claude Link 接力载荷不是有效数据") }

        require(payload.optInt("v", 0) == 1) { "不支持的 Claude Link 接力版本" }
        val source = when (payload.optString("s")) {
            "c" -> "Codex"
            "g" -> "ChatGPT"
            "o" -> "其他对话"
            else -> throw IllegalArgumentException("Claude Link 接力来源无效")
        }
        val title = payload.optString("t").trim()
        val handoff = payload.optString("h").trim()
        val createdAt = payload.optLong("a", 0L)
        val threadId = payload.optString("i").trim().ifEmpty { null }
        require(title.isNotEmpty() && title.length <= MAX_TITLE_CHARS) { "Claude Link 接力标题无效" }
        require(handoff.isNotEmpty() && handoff.length <= MAX_HANDOFF_CHARS) { "Claude Link 接力摘要无效" }
        require(!unsafeControlPattern.containsMatchIn("$title\n$handoff")) { "Claude Link 接力含有不支持的控制字符" }
        require(createdAt > 0L) { "Claude Link 接力时间无效" }
        require(threadId == null || sessionIdPattern.matches(threadId)) { "Claude Link 接力会话 UUID 无效" }
        return ClaudeLinkHandoff(source, title, handoff, createdAt, threadId?.lowercase())
    }

    private fun inflateBounded(compressed: ByteArray): String {
        return try {
            InflaterInputStream(ByteArrayInputStream(compressed), Inflater(true)).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4_096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    require(output.size() <= MAX_DECOMPRESSED_BYTES) { "Claude Link 接力载荷解压后过大" }
                }
                output.toString(Charsets.UTF_8.name())
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: Throwable) {
            throw IllegalArgumentException("Claude Link 接力载荷损坏或无法解压")
        }
    }

    private val unsafeControlPattern = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
}
