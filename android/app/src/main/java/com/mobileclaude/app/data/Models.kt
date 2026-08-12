package com.mobileclaude.app.data

data class ServerProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val hostKey: String = "",
    val fingerprint: String = "",
)

data class HealthInfo(
    val hostname: String,
    val home: String,
    val version: String,
    val retentionDays: Int,
)

data class ChatSummary(
    val id: String,
    val title: String,
    val projectPath: String,
    val mode: String,
    val createdAt: String,
    val updatedAt: String,
    val pinned: Boolean,
    val status: String,
    val preview: String,
    val messageCount: Int,
)

data class ChatMessage(
    val id: Long,
    val role: String,
    val kind: String,
    val content: String,
    val createdAt: String,
    val status: String,
    val metadata: Map<String, String> = emptyMap(),
)

data class Artifact(
    val id: String,
    val name: String,
    val path: String,
    val mimeType: String,
    val size: Long,
    val createdAt: String,
)

data class DeepSeekBalanceInfo(
    val currency: String,
    val totalBalance: String,
    val grantedBalance: String,
    val toppedUpBalance: String,
)

data class DeepSeekBalance(
    val isAvailable: Boolean,
    val balanceInfos: List<DeepSeekBalanceInfo>,
)

data class ChatDetail(
    val chat: ChatSummary,
    val messages: List<ChatMessage>,
    val artifacts: List<Artifact>,
)

data class TerminalCommandReceipt(
    val inputMessageId: Long,
    val outputMessageId: Long,
)

data class RemoteDirectory(
    val name: String,
    val path: String,
)

data class DirectoryListing(
    val path: String,
    val parent: String?,
    val directories: List<RemoteDirectory>,
    val locations: List<RemoteDirectory>,
)

data class RemoteFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedAt: String,
    val mimeType: String,
)

data class RemoteFileListing(
    val path: String,
    val parent: String?,
    val entries: List<RemoteFileEntry>,
    val locations: List<RemoteDirectory>,
)

data class WebAttachment(
    val title: String,
    val url: String,
    val content: String,
)

data class GpuProcessInfo(
    val pid: Int,
    val name: String,
    val memoryUsedMiB: Float?,
    val user: String,
    val running: String,
)

data class GpuInfo(
    val index: Int,
    val uuid: String,
    val name: String,
    val driverVersion: String,
    val temperatureC: Float?,
    val gpuUtilizationPercent: Float?,
    val memoryUtilizationPercent: Float?,
    val memoryUsedMiB: Float?,
    val memoryTotalMiB: Float?,
    val powerDrawW: Float?,
    val powerLimitW: Float?,
    val fanSpeedPercent: Float?,
    val performanceState: String?,
    val graphicsClockMHz: Float?,
    val memoryClockMHz: Float?,
    val processes: List<GpuProcessInfo>,
)

data class GpuQueueJob(
    val id: Int,
    val status: String,
    val gpuCount: Int,
    val gpuIndices: String,
    val pid: Int?,
    val priority: Int,
    val name: String,
    val waited: String,
    val running: String,
)

data class GpuQueueSnapshot(
    val available: Boolean,
    val timestamp: String,
    val reason: String?,
    val message: String?,
    val jobs: List<GpuQueueJob>,
)

data class GpuSnapshot(
    val available: Boolean,
    val timestamp: String,
    val reason: String?,
    val message: String?,
    val driverVersion: String,
    val processesAvailable: Boolean,
    val gpus: List<GpuInfo>,
    val queue: GpuQueueSnapshot,
)

enum class MainTab { CHATS, BROWSER, FILES, GPU, SERVERS }

sealed interface TerminalStatus {
    data object Disconnected : TerminalStatus
    data object Connecting : TerminalStatus
    data object Connected : TerminalStatus
    data class Error(val message: String) : TerminalStatus
}

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data class Connecting(val message: String) : ConnectionStatus
    data class Connected(val health: HealthInfo) : ConnectionStatus
    data class Failed(val message: String) : ConnectionStatus
}
