package com.mobileclaude.app.network

import com.mobileclaude.app.data.DeepSeekMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class DeepSeekChatClient {
    suspend fun stream(
        apiKey: ByteArray,
        messages: List<DeepSeekMessage>,
        onDelta: suspend (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val connection = URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${apiKey.toString(Charsets.UTF_8)}")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "text/event-stream")
            val requestMessages = JSONArray()
            messages.forEach { requestMessages.put(JSONObject().put("role", it.role).put("content", it.content)) }
            val body = JSONObject()
                .put("model", "deepseek-flash")
                .put("thinking", JSONObject().put("type", "disabled"))
                .put("messages", requestMessages)
                .put("stream", true)
                .toString()
                .toByteArray(Charsets.UTF_8)
            try {
                connection.outputStream.use { it.write(body) }
            } finally {
                body.fill(0)
            }
            if (connection.responseCode !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val detail = runCatching { JSONObject(error).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "DeepSeek 请求失败 (${connection.responseCode})")
            }
            var finished = false
            connection.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    coroutineContext.ensureActive()
                    if (!line.startsWith("data: ")) continue
                    val payload = line.removePrefix("data: ").trim()
                    if (payload == "[DONE]") {
                        finished = true
                        break
                    }
                    val chunk = JSONObject(payload)
                    val delta = chunk.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("delta")?.optString("content").orEmpty()
                    if (delta.isNotEmpty()) onDelta(delta)
                }
            }
            if (!finished) throw IllegalStateException("DeepSeek 回答中断，请重试")
        } finally {
            connection.disconnect()
        }
    }
}
