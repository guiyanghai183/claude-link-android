package com.mobileclaude.app.network

import com.mobileclaude.app.data.Artifact
import com.mobileclaude.app.data.ChatDetail
import com.mobileclaude.app.data.ChatMessage
import com.mobileclaude.app.data.ChatSummary
import com.mobileclaude.app.data.DirectoryListing
import com.mobileclaude.app.data.DeepSeekBalance
import com.mobileclaude.app.data.DeepSeekBalanceInfo
import com.mobileclaude.app.data.GpuInfo
import com.mobileclaude.app.data.GpuProcessInfo
import com.mobileclaude.app.data.GpuQueueJob
import com.mobileclaude.app.data.GpuQueueSnapshot
import com.mobileclaude.app.data.GpuSnapshot
import com.mobileclaude.app.data.HealthInfo
import com.mobileclaude.app.data.RemoteDirectory
import com.mobileclaude.app.data.RemoteFileEntry
import com.mobileclaude.app.data.RemoteFileListing
import com.mobileclaude.app.data.TerminalCommandReceipt
import com.mobileclaude.app.data.WebAttachment
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class BridgeApi(private val localPort: Int) {
    private val base = "http://127.0.0.1:$localPort"

    fun health(): HealthInfo {
        val json = request(
            method = "GET",
            path = "/health",
            connectTimeoutMillis = 2_000,
            readTimeoutMillis = 2_000,
        )
        return HealthInfo(
            hostname = json.getString("hostname"),
            home = json.getString("home"),
            version = json.getString("version"),
            retentionDays = json.optInt("retentionDays", 7),
        )
    }

    fun listChats(): List<ChatSummary> = request("GET", "/v1/chats")
        .getJSONArray("chats")
        .mapObjects(::parseChat)

    fun createChat(projectPath: String, clientChatId: String, mode: String): ChatSummary = parseChat(
        request(
            "POST",
            "/v1/chats",
            JSONObject()
                .put("projectPath", projectPath)
                .put("clientChatId", clientChatId)
                .put("mode", mode),
        )
    )

    fun startTerminalCommand(
        chatId: String,
        command: String,
        clientCommandId: String,
    ): TerminalCommandReceipt {
        val json = request(
            "POST",
            "/v1/chats/$chatId/terminal/commands",
            JSONObject()
                .put("command", command)
                .put("clientCommandId", clientCommandId),
        )
        return TerminalCommandReceipt(
            inputMessageId = json.getLong("inputMessageId"),
            outputMessageId = json.getLong("outputMessageId"),
        )
    }

    fun prepareTerminalChat(chatId: String) {
        request("POST", "/v1/chats/$chatId/terminal/open", JSONObject())
    }

    fun updateTerminalOutput(
        chatId: String,
        messageId: Long,
        content: String,
        complete: Boolean,
    ) {
        request(
            "PATCH",
            "/v1/chats/$chatId/terminal/outputs/$messageId",
            JSONObject()
                .put("content", content)
                .put("status", if (complete) "complete" else "streaming"),
        )
    }

    fun getChat(chatId: String): ChatDetail {
        val json = request("GET", "/v1/chats/$chatId")
        return ChatDetail(
            chat = parseChat(json),
            messages = json.getJSONArray("messages").mapObjects(::parseMessage),
            artifacts = json.getJSONArray("artifacts").mapObjects(::parseArtifact),
        )
    }

    fun sendMessage(
        chatId: String,
        text: String,
        attachment: WebAttachment?,
        clientMessageId: String,
    ) {
        val attachments = JSONArray()
        if (attachment != null) {
            attachments.put(
                JSONObject()
                    .put("kind", "web")
                    .put("title", attachment.title)
                    .put("url", attachment.url)
                    .put("content", attachment.content)
            )
        }
        request(
            "POST",
            "/v1/chats/$chatId/messages",
            JSONObject()
                .put("text", text)
                .put("attachments", attachments)
                .put("clientMessageId", clientMessageId),
        )
    }

    fun updateChat(chatId: String, pinned: Boolean? = null, projectPath: String? = null): ChatSummary {
        val body = JSONObject()
        pinned?.let { body.put("pinned", it) }
        projectPath?.let { body.put("projectPath", it) }
        return parseChat(request("PATCH", "/v1/chats/$chatId", body))
    }

    fun deleteChat(chatId: String) {
        request("DELETE", "/v1/chats/$chatId")
    }

    fun interrupt(chatId: String) {
        request("POST", "/v1/chats/$chatId/interrupt", JSONObject())
    }

    fun resolveApproval(chatId: String, messageId: Long, allow: Boolean) {
        request(
            "POST",
            "/v1/chats/$chatId/approvals/$messageId/resolve",
            JSONObject().put("decision", if (allow) "allow" else "deny"),
        )
    }

    fun listDirectories(path: String? = null): DirectoryListing {
        val suffix = path?.let { "?path=" + encodeQueryParameter(it) }.orEmpty()
        val json = request("GET", "/v1/directories$suffix")
        return DirectoryListing(
            path = json.getString("path"),
            parent = json.optString("parent").takeIf { it.isNotBlank() && it != "null" },
            directories = json.getJSONArray("directories").mapObjects {
                RemoteDirectory(it.getString("name"), it.getString("path"))
            },
            locations = json.optJSONArray("locations")?.mapObjects {
                RemoteDirectory(it.getString("name"), it.getString("path"))
            }.orEmpty(),
        )
    }

    fun directorySuggestions(path: String): List<RemoteDirectory> {
        if (path.isBlank()) return emptyList()
        val json = request(
            method = "GET",
            path = "/v1/directories/suggestions?path=${encodeQueryParameter(path)}",
            connectTimeoutMillis = 2_000,
            readTimeoutMillis = 4_000,
        )
        return json.optJSONArray("suggestions")?.mapObjects {
            RemoteDirectory(it.getString("name"), it.getString("path"))
        }.orEmpty()
    }

    fun fileListing(path: String? = null): RemoteFileListing {
        val suffix = path
            ?.takeIf { it.isNotBlank() }
            ?.let { "?path=" + encodeQueryParameter(it) }
            .orEmpty()
        val json = request(
            method = "GET",
            path = "/v1/files$suffix",
            connectTimeoutMillis = 3_000,
            readTimeoutMillis = 10_000,
        )
        return RemoteFileListing(
            path = json.getString("path"),
            parent = json.optionalString("parent"),
            entries = json.optJSONArray("entries")?.mapObjects { entry ->
                RemoteFileEntry(
                    name = entry.getString("name"),
                    path = entry.getString("path"),
                    isDirectory = entry.optBoolean("isDirectory"),
                    size = entry.optLong("size"),
                    modifiedAt = entry.optString("modifiedAt"),
                    mimeType = entry.optString("mimeType"),
                )
            }.orEmpty(),
            locations = json.optJSONArray("locations")?.mapObjects { entry ->
                RemoteDirectory(entry.getString("name"), entry.getString("path"))
            }.orEmpty(),
        )
    }

    fun fileContentUrl(path: String): String {
        require(path.isNotBlank()) { "文件路径不能为空" }
        return "$base/v1/files/content?path=${encodeQueryParameter(path)}"
    }

    fun fileBytes(path: String, maxBytes: Int = DEFAULT_FILE_PREVIEW_MAX_BYTES): ByteArray {
        require(maxBytes in 1..ABSOLUTE_FILE_PREVIEW_MAX_BYTES) {
            "预览大小上限必须在 1 字节到 ${formatByteCount(ABSOLUTE_FILE_PREVIEW_MAX_BYTES.toLong())} 之间"
        }
        val connection = URI(fileContentUrl(path)).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "*/*")
        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?.let { errorText ->
                        runCatching { JSONObject(errorText).optString("error") }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                    }
                    ?: "文件加载失败（HTTP $status）"
                throw IOException(message)
            }

            val declaredLength = connection.contentLengthLong
            if (declaredLength > maxBytes) {
                throw IOException(
                    "文件过大，无法预览（${formatByteCount(declaredLength)}，上限 ${formatByteCount(maxBytes.toLong())}）"
                )
            }

            connection.inputStream.use { input ->
                val initialCapacity = declaredLength
                    .takeIf { it in 1..maxBytes.toLong() }
                    ?.toInt()
                    ?: minOf(DEFAULT_STREAM_BUFFER_BYTES, maxBytes)
                val output = ByteArrayOutputStream(initialCapacity)
                val buffer = ByteArray(DEFAULT_STREAM_BUFFER_BYTES)
                var totalBytes = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    if (totalBytes > maxBytes) {
                        throw IOException("文件过大，无法预览（上限 ${formatByteCount(maxBytes.toLong())}）")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    fun artifactBytes(artifactId: String): ByteArray {
        val connection = URI("$base/v1/artifacts/$artifactId/content").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        return try {
            if (connection.responseCode !in 200..299) throw IOException("图片加载失败")
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    fun artifactContentUrl(artifactId: String): String = "$base/v1/artifacts/$artifactId/content"

    fun gpuStatus(): GpuSnapshot {
        val json = request(
            method = "GET",
            path = "/v1/system/gpus",
            connectTimeoutMillis = 2_000,
            readTimeoutMillis = 5_000,
        )
        val queue = json.optJSONObject("queue")
        return GpuSnapshot(
            available = json.optBoolean("available"),
            timestamp = json.optString("timestamp"),
            reason = json.optionalString("reason"),
            message = json.optionalString("message"),
            driverVersion = json.optString("driverVersion"),
            processesAvailable = json.optBoolean("processesAvailable"),
            gpus = json.optJSONArray("gpus")?.mapObjects { gpu ->
                GpuInfo(
                    index = gpu.optInt("index"),
                    uuid = gpu.optString("uuid"),
                    name = gpu.optString("name"),
                    driverVersion = gpu.optString("driverVersion"),
                    temperatureC = gpu.optionalFloat("temperatureC"),
                    gpuUtilizationPercent = gpu.optionalFloat("gpuUtilizationPercent"),
                    memoryUtilizationPercent = gpu.optionalFloat("memoryUtilizationPercent"),
                    memoryUsedMiB = gpu.optionalFloat("memoryUsedMiB"),
                    memoryTotalMiB = gpu.optionalFloat("memoryTotalMiB"),
                    powerDrawW = gpu.optionalFloat("powerDrawW"),
                    powerLimitW = gpu.optionalFloat("powerLimitW"),
                    fanSpeedPercent = gpu.optionalFloat("fanSpeedPercent"),
                    performanceState = gpu.optionalString("performanceState"),
                    graphicsClockMHz = gpu.optionalFloat("graphicsClockMHz"),
                    memoryClockMHz = gpu.optionalFloat("memoryClockMHz"),
                    processes = gpu.optJSONArray("processes")?.mapObjects { process ->
                        GpuProcessInfo(
                            pid = process.optInt("pid"),
                            name = process.optString("name"),
                            memoryUsedMiB = process.optionalFloat("memoryUsedMiB"),
                        )
                    }.orEmpty(),
                )
            }.orEmpty(),
            queue = if (queue == null) {
                GpuQueueSnapshot(
                    available = false,
                    timestamp = json.optString("timestamp"),
                    reason = "bridge_outdated",
                    message = "服务器组件版本过旧，请重新连接以同步更新",
                    jobs = emptyList(),
                )
            } else {
                GpuQueueSnapshot(
                    available = queue.optBoolean("available"),
                    timestamp = queue.optString("timestamp"),
                    reason = queue.optionalString("reason"),
                    message = queue.optionalString("message"),
                    jobs = queue.optJSONArray("jobs")?.mapObjects { job ->
                        GpuQueueJob(
                            id = job.optInt("id"),
                            status = job.optString("status"),
                            gpuCount = job.optInt("gpuCount"),
                            gpuIndices = job.optString("gpuIndices"),
                            pid = if (job.isNull("pid")) null else job.optInt("pid"),
                            priority = job.optInt("priority"),
                            name = job.optString("name"),
                            waited = job.optString("waited"),
                            running = job.optString("running"),
                        )
                    }.orEmpty(),
                )
            },
        )
    }

    fun deepSeekBalance(apiKey: ByteArray): DeepSeekBalance {
        require(apiKey.isNotEmpty()) { "请先设置 DeepSeek API Key" }
        val json = request(
            "POST",
            "/v1/deepseek/balance",
            JSONObject().put("apiKey", apiKey.toString(Charsets.UTF_8)),
        )
        return DeepSeekBalance(
            isAvailable = json.optBoolean("isAvailable"),
            balanceInfos = json.optJSONArray("balanceInfos")?.mapObjects {
                DeepSeekBalanceInfo(
                    currency = it.optString("currency"),
                    totalBalance = it.optString("totalBalance", "0"),
                    grantedBalance = it.optString("grantedBalance", "0"),
                    toppedUpBalance = it.optString("toppedUpBalance", "0"),
                )
            }.orEmpty(),
        )
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        connectTimeoutMillis: Int = 10_000,
        readTimeoutMillis: Int = 35_000,
    ): JSONObject {
        val connection = URI(base + path).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.setRequestProperty("Accept", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) throw IOException(json.optString("error", "请求失败（$status）"))
            json
        } finally {
            connection.disconnect()
        }
    }

    private fun parseChat(json: JSONObject) = ChatSummary(
        id = json.getString("id"),
        title = json.getString("title"),
        projectPath = json.getString("projectPath"),
        mode = json.optString("mode", "claude"),
        createdAt = json.optString("createdAt"),
        updatedAt = json.optString("updatedAt"),
        pinned = json.optBoolean("pinned"),
        status = json.optString("status", "idle"),
        preview = json.optString("preview"),
        messageCount = json.optInt("messageCount"),
    )

    private fun parseMessage(json: JSONObject): ChatMessage {
        val metadataJson = json.optJSONObject("metadata") ?: JSONObject()
        val metadata = buildMap {
            metadataJson.keys().forEach { key ->
                val value = metadataJson.opt(key)
                if (value != null && value !is JSONObject && value !is JSONArray) put(key, value.toString())
            }
        }
        return ChatMessage(
            id = json.getLong("id"),
            role = json.getString("role"),
            kind = json.optString("kind", "text"),
            content = json.optString("content"),
            createdAt = json.optString("createdAt"),
            status = json.optString("status", "complete"),
            metadata = metadata,
        )
    }

    private fun parseArtifact(json: JSONObject) = Artifact(
        id = json.getString("id"),
        name = json.getString("name"),
        path = json.getString("path"),
        mimeType = json.getString("mimeType"),
        size = json.optLong("size"),
        createdAt = json.optString("createdAt"),
    )

    private fun encodeQueryParameter(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun formatByteCount(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private companion object {
        const val DEFAULT_FILE_PREVIEW_MAX_BYTES = 16 * 1024 * 1024
        const val ABSOLUTE_FILE_PREVIEW_MAX_BYTES = 32 * 1024 * 1024
        const val DEFAULT_STREAM_BUFFER_BYTES = 8 * 1024
    }
}

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = buildList {
    for (index in 0 until length()) add(transform(getJSONObject(index)))
}

private fun JSONObject.optionalFloat(name: String): Float? {
    if (!has(name) || isNull(name)) return null
    val value = opt(name)
    return when (value) {
        is Number -> value.toFloat().takeIf { it.isFinite() }
        is String -> value.toFloatOrNull()?.takeIf { it.isFinite() }
        else -> null
    }
}

private fun JSONObject.optionalString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
}
