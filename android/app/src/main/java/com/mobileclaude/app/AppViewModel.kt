package com.mobileclaude.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileclaude.app.data.ChatDetail
import com.mobileclaude.app.data.ChatSummary
import com.mobileclaude.app.data.ConnectionStatus
import com.mobileclaude.app.data.DeepSeekBalance
import com.mobileclaude.app.data.DirectoryListing
import com.mobileclaude.app.data.GpuSnapshot
import com.mobileclaude.app.data.MainTab
import com.mobileclaude.app.data.ProfileRepository
import com.mobileclaude.app.data.RemoteDirectory
import com.mobileclaude.app.data.RemoteFileEntry
import com.mobileclaude.app.data.RemoteFileListing
import com.mobileclaude.app.data.ServerProfile
import com.mobileclaude.app.data.TerminalStatus
import com.mobileclaude.app.data.WebAttachment
import com.mobileclaude.app.data.UpdateState
import com.mobileclaude.app.network.BridgeApi
import com.mobileclaude.app.security.CredentialVault
import com.mobileclaude.app.ssh.SshTunnelManager
import com.mobileclaude.app.ssh.SshTerminalSession
import com.mobileclaude.app.ssh.TunnelConnection
import com.mobileclaude.app.ssh.ReconnectDelayPolicy
import com.mobileclaude.app.terminal.TerminalTextBuffer
import com.mobileclaude.app.update.GitHubUpdateManager
import com.mobileclaude.app.update.InstallLaunchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

private const val MAX_WEB_ATTACHMENT_CHARS = 300_000

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val profileRepository = ProfileRepository(application)
    private val vault = CredentialVault(application)
    private val tunnel = SshTunnelManager(application, profileRepository, vault)
    private val updateManager = GitHubUpdateManager(application)
    private var api: BridgeApi? = null
    private var pollJob: Job? = null
    private var chatLoadJob: Job? = null
    private var gpuPollJob: Job? = null
    private var reconnectJob: Job? = null
    private var connectionHealthJob: Job? = null
    private var networkValidationJob: Job? = null
    private var folderSuggestionJob: Job? = null
    private var remoteSuggestionJob: Job? = null
    private var terminalReaderJob: Job? = null
    private var terminalPersistJob: Job? = null
    private var terminalSession: SshTerminalSession? = null
    private var terminalGeneration = 0L
    private var terminalChatId: String? = null
    private var terminalCompletionMarker: String? = null
    private val terminalBuffer = TerminalTextBuffer()
    private val reconnectMutex = Mutex()
    private var connectionGeneration = 0L
    private var activeChatGeneration = 0L
    private var busyTaskCount = 0
    private val pendingWebAttachments = mutableStateMapOf<String, WebAttachment>()
    private var ocrPreviewTargetChatId: String? = null

    val profiles = mutableStateListOf<ServerProfile>().apply { addAll(profileRepository.load()) }
    val chats = mutableStateListOf<ChatSummary>()
    val artifactImages = mutableStateMapOf<String, Bitmap>()

    var activeProfile by mutableStateOf<ServerProfile?>(null)
        private set
    var connectionStatus by mutableStateOf<ConnectionStatus>(ConnectionStatus.Disconnected)
        private set
    var activeChat by mutableStateOf<ChatDetail?>(null)
        private set
    var selectedTab by mutableStateOf(MainTab.CHATS)
        private set
    val pendingWebAttachment: WebAttachment?
        get() = activeChat?.chat?.id?.let(pendingWebAttachments::get)
    var ocrPreviewDraft by mutableStateOf<WebAttachment?>(null)
        private set
    var folderListing by mutableStateOf<DirectoryListing?>(null)
        private set
    var folderPickerVisible by mutableStateOf(false)
        private set
    var folderPathSuggestions by mutableStateOf<List<RemoteDirectory>>(emptyList())
        private set
    var newChatFolder by mutableStateOf<String?>(null)
        private set
    var addServerVisible by mutableStateOf(profiles.isEmpty())
    var busy by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var deepSeekBalance by mutableStateOf<DeepSeekBalance?>(null)
        private set
    var deepSeekBusy by mutableStateOf(false)
        private set
    var deepSeekConfigured by mutableStateOf(false)
        private set
    var gpuSnapshot by mutableStateOf<GpuSnapshot?>(null)
        private set
    var gpuBusy by mutableStateOf(false)
        private set
    var gpuError by mutableStateOf<String?>(null)
        private set
    var terminalStatus by mutableStateOf<TerminalStatus>(TerminalStatus.Disconnected)
        private set
    var terminalCommandSending by mutableStateOf(false)
        private set
    var terminalCommandRunning by mutableStateOf(false)
        private set
    var terminalLiveOutput by mutableStateOf("")
        private set
    var terminalLiveOutputMessageId by mutableStateOf<Long?>(null)
        private set
    var remoteFileListing by mutableStateOf<RemoteFileListing?>(null)
        private set
    var remoteFilesBusy by mutableStateOf(false)
        private set
    var remotePathSuggestions by mutableStateOf<List<RemoteDirectory>>(emptyList())
        private set
    var remoteFilePreview by mutableStateOf<RemoteFileEntry?>(null)
        private set
    var remoteFilePreviewBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var remoteFilePreviewText by mutableStateOf<String?>(null)
        private set
    var remoteFilePreviewBusy by mutableStateOf(false)
        private set
    var remoteFilePreviewError by mutableStateOf<String?>(null)
        private set

    private val connectivityManager = application.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleNetworkValidation()

        override fun onLost(network: Network) = scheduleNetworkValidation()

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            scheduleNetworkValidation()
    }
    private var networkCallbackRegistered = false

    init {
        networkCallbackRegistered = runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            connectivityManager != null
        }.getOrDefault(false)
        checkForUpdates(silent = true)
        restoreLastConnectedProfile()
    }

    private fun restoreLastConnectedProfile() {
        val profileId = profileRepository.lastConnectedProfileId() ?: return
        val profile = profiles.firstOrNull { it.id == profileId } ?: run {
            profileRepository.setLastConnectedProfileId(null)
            return
        }
        activeProfile = profile
        connectionStatus = ConnectionStatus.Connecting("正在自动恢复上次连接的服务器…")
        scheduleTunnelRecovery("正在自动恢复上次连接的服务器…")
    }

    private fun scheduleNetworkValidation() {
        viewModelScope.launch {
            networkValidationJob?.cancel()
            networkValidationJob = viewModelScope.launch {
                delay(NETWORK_SETTLE_DELAY_MILLIS)
                val profile = activeProfile ?: return@launch
                val bridge = api
                val healthy = bridge?.let { candidate ->
                    runCatching { withContext(Dispatchers.IO) { candidate.health() } }.isSuccess
                } ?: false
                if (!healthy && activeProfile?.id == profile.id) {
                    if (api === bridge) api = null
                    scheduleTunnelRecovery("检测到 VPN 或网络变化，正在恢复连接…")
                }
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun showError(message: String) {
        errorMessage = message
    }

    fun checkForUpdates(silent: Boolean = false) {
        if (updateState is UpdateState.Checking || updateState is UpdateState.Downloading) return
        if (!updateManager.isConfigured()) {
            if (!silent) errorMessage = "GitHub 更新仓库尚未配置"
            return
        }
        viewModelScope.launch {
            updateState = UpdateState.Checking
            try {
                val update = withContext(Dispatchers.IO) { updateManager.checkForUpdate() }
                updateState = when {
                    update != null -> UpdateState.Available(update)
                    silent -> UpdateState.Idle
                    else -> UpdateState.UpToDate
                }
            } catch (error: Throwable) {
                if (error !is CancellationException) {
                    updateState = if (silent) UpdateState.Idle else {
                        UpdateState.Error(error.userMessage())
                    }
                }
            }
        }
    }

    fun downloadUpdate() {
        val update = when (val state = updateState) {
            is UpdateState.Available -> state.update
            is UpdateState.Ready -> {
                installUpdate()
                return
            }
            else -> return
        }
        viewModelScope.launch {
            updateState = UpdateState.Downloading(update)
            try {
                val apk = withContext(Dispatchers.IO) { updateManager.download(update) }
                updateState = UpdateState.Ready(update, apk.absolutePath)
                installUpdate()
            } catch (error: Throwable) {
                if (error !is CancellationException) {
                    updateState = UpdateState.Error(error.userMessage())
                }
            }
        }
    }

    fun installUpdate() {
        val state = updateState as? UpdateState.Ready ?: return
        try {
            when (updateManager.launchInstaller(java.io.File(state.apkPath))) {
                InstallLaunchResult.Launched -> Unit
                InstallLaunchResult.PermissionRequired -> {
                    errorMessage = "请允许 Claude Link 安装未知应用，返回后再点“继续安装”"
                }
            }
        } catch (error: Throwable) {
            updateState = UpdateState.Error(error.userMessage())
        }
    }

    fun dismissUpdate() {
        if (updateState !is UpdateState.Downloading && updateState !is UpdateState.Checking) {
            updateState = UpdateState.Idle
        }
    }

    fun connect(profile: ServerProfile) {
        if (busy || (connectionStatus is ConnectionStatus.Connecting && reconnectJob?.isActive != true)) return
        connectionStatus = ConnectionStatus.Connecting("准备连接…")
        viewModelScope.launch {
            runTask {
                try {
                    prepareForManualConnection()
                    activeProfile = profile
                    profileRepository.setLastConnectedProfileId(profile.id)
                    val connection = reconnectMutex.withLock {
                        val opened = tunnel.connect(profile) { message ->
                            connectionStatus = ConnectionStatus.Connecting(message)
                        }
                        connectionStatus = ConnectionStatus.Connecting("等待服务器组件就绪…")
                        val (bridge, health) = verifyConnection(opened)
                        api = bridge
                        connectionStatus = ConnectionStatus.Connected(health)
                        opened
                    }
                    refreshDeepSeekConfiguration(profile)
                    selectedTab = MainTab.CHATS
                    refreshChatsInternal()
                    startConnectionHealthMonitor()
                    connection.bridgeWarning?.let { errorMessage = it }
                } catch (error: Throwable) {
                    if (error.isTunnelInterruption()) {
                        activeProfile = profile
                        profileRepository.setLastConnectedProfileId(profile.id)
                        connectionStatus = ConnectionStatus.Connecting("连接暂时中断，正在自动重试…")
                        scheduleTunnelRecovery("连接暂时中断，正在自动重试…")
                        return@runTask
                    }
                    profileRepository.setLastConnectedProfileId(null)
                    activeProfile = null
                    throw error
                }
            }
        }
    }

    fun enroll(
        name: String,
        host: String,
        port: Int,
        username: String,
        password: String,
    ) {
        if (busy || connectionStatus is ConnectionStatus.Connecting) return
        connectionStatus = ConnectionStatus.Connecting("准备首次连接…")
        viewModelScope.launch {
            runTask {
                prepareForManualConnection()
                val connection = reconnectMutex.withLock {
                    val opened = tunnel.enroll(name, host, port, username, password) { message ->
                        connectionStatus = ConnectionStatus.Connecting(message)
                    }
                    connectionStatus = ConnectionStatus.Connecting("等待服务器组件就绪…")
                    val (bridge, health) = verifyConnection(opened)
                    api = bridge
                    activeProfile = opened.profile
                    profileRepository.setLastConnectedProfileId(opened.profile.id)
                    connectionStatus = ConnectionStatus.Connected(health)
                    opened
                }
                profiles.clear()
                profiles.addAll(profileRepository.load())
                refreshDeepSeekConfiguration(connection.profile)
                addServerVisible = false
                selectedTab = MainTab.CHATS
                refreshChatsInternal()
                startConnectionHealthMonitor()
                connection.bridgeWarning?.let { errorMessage = it }
            }
        }
    }

    fun disconnect() {
        connectionGeneration += 1
        reconnectJob?.cancel()
        reconnectJob = null
        connectionHealthJob?.cancel()
        connectionHealthJob = null
        networkValidationJob?.cancel()
        networkValidationJob = null
        activeChatGeneration += 1
        chatLoadJob?.cancel()
        chatLoadJob = null
        pollJob?.cancel()
        pollJob = null
        stopGpuMonitoring()
        stopTerminalSession(persistOutput = false)
        tunnel.disconnect()
        profileRepository.setLastConnectedProfileId(null)
        api = null
        activeProfile = null
        activeChat = null
        pendingWebAttachments.clear()
        ocrPreviewTargetChatId = null
        ocrPreviewDraft = null
        chats.clear()
        artifactImages.clear()
        deepSeekBalance = null
        deepSeekConfigured = false
        deepSeekBusy = false
        gpuSnapshot = null
        gpuError = null
        newChatFolder = null
        folderPathSuggestions = emptyList()
        remotePathSuggestions = emptyList()
        clearRemoteFileState()
        connectionStatus = ConnectionStatus.Disconnected
    }

    fun selectTab(tab: MainTab) {
        if (selectedTab == tab) return
        selectedTab = tab
        if (tab == MainTab.CHATS) {
            val chatId = activeChat?.chat?.id ?: return
            val generation = activeChatGeneration
            viewModelScope.launch {
                runCatching { refreshActiveChat(chatId, generation) }
                startPolling(chatId, generation)
            }
        } else {
            pollJob?.cancel()
            pollJob = null
        }
        if (tab == MainTab.FILES && remoteFileListing == null) refreshRemoteFiles()
    }

    fun deleteProfile(profile: ServerProfile) {
        if (activeProfile?.id == profile.id) disconnect()
        profileRepository.delete(profile.id)
        vault.delete(profile.id)
        vault.deleteSecret(deepSeekSecretName(profile.id))
        profiles.removeAll { it.id == profile.id }
    }

    fun refreshChats() {
        viewModelScope.launch { runTask { refreshChatsInternal() } }
    }

    fun createChat(projectPath: String? = null, mode: String = "claude") {
        if (mode !in setOf("claude", "terminal")) return
        val clientChatId = UUID.randomUUID().toString()
        val generation = beginChatSelection()
        chatLoadJob?.cancel()
        chatLoadJob = null
        viewModelScope.launch {
            runTask {
                val home = (connectionStatus as? ConnectionStatus.Connected)?.health?.home
                    ?: error("服务器尚未连接")
                val created = callBridge {
                    it.createChat(projectPath ?: home, clientChatId, mode)
                }
                refreshChatsInternal()
                openChatInternal(created.id, generation)
            }
        }
    }

    fun openChat(chatId: String) {
        val generation = beginChatSelection()
        chatLoadJob?.cancel()
        chatLoadJob = viewModelScope.launch {
            runTask { openChatInternal(chatId, generation) }
        }
    }

    fun closeChat() {
        stopTerminalSession()
        activeChatGeneration += 1
        chatLoadJob?.cancel()
        chatLoadJob = null
        pollJob?.cancel()
        pollJob = null
        activeChat = null
        if (activeProfile != null) refreshChats()
    }

    fun sendMessage(text: String, onAccepted: () -> Unit = {}) {
        val detail = activeChat ?: return
        if (detail.chat.mode != "claude") return
        val chatId = detail.chat.id
        val generation = activeChatGeneration
        val attachment = pendingWebAttachments[chatId]
        if (text.isBlank() && attachment == null) return
        val clientMessageId = UUID.randomUUID().toString()
        viewModelScope.launch {
            try {
                callBridge {
                    it.sendMessage(chatId, text.trim(), attachment, clientMessageId)
                }
                if (pendingWebAttachments[chatId] == attachment) {
                    pendingWebAttachments.remove(chatId)
                }
                runCatching(onAccepted)
                refreshActiveChat(chatId, generation)
                startPolling(chatId, generation)
            } catch (error: Throwable) {
                if (error !is CancellationException) errorMessage = error.userMessage()
            }
        }
    }

    fun reconnectTerminal() {
        val chat = activeChat?.chat ?: return
        if (chat.mode != "terminal") return
        startTerminalSession(chat.id, chat.projectPath)
    }

    fun sendTerminalCommand(command: String, onAccepted: () -> Unit = {}) {
        val detail = activeChat ?: return
        if (detail.chat.mode != "terminal" || terminalCommandSending) return
        val opened = terminalSession?.takeIf { it.isConnected } ?: run {
            errorMessage = "远程终端尚未连接"
            return
        }
        if (terminalCommandRunning) {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { opened.write(command + "\r") }
                    runCatching(onAccepted)
                } catch (error: Throwable) {
                    if (error !is CancellationException) {
                        terminalStatus = TerminalStatus.Error(error.userMessage())
                    }
                }
            }
            return
        }
        if (command.isBlank()) {
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { opened.write("\r") } }
                runCatching(onAccepted)
            }
            return
        }
        if (command.length > MAX_TERMINAL_COMMAND_CHARS) {
            errorMessage = "单条终端命令不能超过 $MAX_TERMINAL_COMMAND_CHARS 个字符"
            return
        }
        val chatId = detail.chat.id
        val generation = terminalGeneration
        val clientCommandId = UUID.randomUUID().toString()
        terminalCommandSending = true
        viewModelScope.launch {
            try {
                val receipt = callBridge {
                    it.startTerminalCommand(chatId, command, clientCommandId)
                }
                if (
                    generation != terminalGeneration ||
                    terminalChatId != chatId ||
                    activeChat?.chat?.id != chatId
                ) {
                    return@launch
                }
                val currentSession = terminalSession?.takeIf { it.isConnected }
                    ?: throw IOException("远程终端连接已经结束")
                terminalPersistJob?.cancel()
                terminalPersistJob = null
                terminalBuffer.clear()
                terminalLiveOutput = ""
                terminalLiveOutputMessageId = receipt.outputMessageId
                terminalCompletionMarker =
                    "__CLAUDE_LINK_DONE_${clientCommandId.replace("-", "").uppercase()}__:"
                terminalCommandRunning = true
                refreshActiveChat(chatId, activeChatGeneration)
                withContext(Dispatchers.IO) {
                    currentSession.execute(command, terminalCompletionMarker!!)
                }
                runCatching(onAccepted)
            } catch (error: Throwable) {
                if (error !is CancellationException) {
                    terminalCommandRunning = false
                    terminalCompletionMarker = null
                    terminalStatus = TerminalStatus.Error(error.userMessage())
                    errorMessage = error.userMessage()
                }
            } finally {
                if (generation == terminalGeneration) terminalCommandSending = false
            }
        }
    }

    fun sendTerminalControl(code: Int) {
        val opened = terminalSession?.takeIf { it.isConnected } ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { opened.sendControl(code) }
            } catch (error: Throwable) {
                if (error !is CancellationException) {
                    terminalStatus = TerminalStatus.Error(error.userMessage())
                }
            }
        }
    }

    private fun startTerminalSession(chatId: String, projectPath: String) {
        stopTerminalSession()
        val generation = terminalGeneration
        terminalChatId = chatId
        terminalStatus = TerminalStatus.Connecting
        terminalCommandSending = false
        viewModelScope.launch {
            try {
                callBridge { it.prepareTerminalChat(chatId) }
                val opened = withContext(Dispatchers.IO) {
                    tunnel.openTerminal(projectPath)
                }
                if (
                    generation != terminalGeneration ||
                    activeChat?.chat?.id != chatId ||
                    activeChat?.chat?.mode != "terminal"
                ) {
                    opened.close()
                    return@launch
                }
                terminalSession = opened
                terminalStatus = TerminalStatus.Connected
                terminalReaderJob = viewModelScope.launch(Dispatchers.IO) {
                    val buffer = CharArray(4_096)
                    var failure: Throwable? = null
                    try {
                        while (isActive) {
                            val count = opened.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            val chunk = String(buffer, 0, count)
                            withContext(Dispatchers.Main) {
                                handleTerminalChunk(generation, chunk)
                            }
                        }
                    } catch (error: Throwable) {
                        if (error !is CancellationException) failure = error
                    } finally {
                        withContext(Dispatchers.Main) {
                            if (generation == terminalGeneration) {
                                terminalSession = null
                                terminalCommandRunning = false
                                terminalCompletionMarker = null
                                terminalStatus = failure?.let {
                                    TerminalStatus.Error(it.userMessage())
                                } ?: TerminalStatus.Disconnected
                                completeTerminalOutput(
                                    generation,
                                    disconnected = true,
                                )
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                if (generation == terminalGeneration && error !is CancellationException) {
                    terminalStatus = TerminalStatus.Error(error.userMessage())
                    terminalSession = null
                }
            }
        }
    }

    private fun handleTerminalChunk(generation: Long, chunk: String) {
        if (
            generation != terminalGeneration ||
            terminalLiveOutputMessageId == null ||
            !terminalCommandRunning
        ) {
            return
        }
        val rendered = terminalBuffer.append(chunk)
        val marker = terminalCompletionMarker
        if (marker != null) {
            val completion = Regex(Regex.escape(marker) + "(-?\\d+)").find(rendered)
            if (completion != null) {
                val exitCode = completion.groupValues[1].toIntOrNull() ?: -1
                val output = rendered.substring(0, completion.range.first).trimEnd().ifBlank {
                    "（命令执行完成，没有输出）"
                }
                terminalLiveOutput = if (exitCode == 0) {
                    output
                } else {
                    "$output\n[退出状态 $exitCode]"
                }
                terminalCommandRunning = false
                terminalCompletionMarker = null
                completeTerminalOutput(generation, disconnected = false)
                return
            }
        }
        terminalLiveOutput = rendered
        scheduleTerminalPersist(generation)
    }

    private fun scheduleTerminalPersist(generation: Long) {
        if (terminalPersistJob?.isActive == true) return
        terminalPersistJob = viewModelScope.launch {
            delay(TERMINAL_PERSIST_INTERVAL_MILLIS)
            if (generation != terminalGeneration) return@launch
            val chatId = terminalChatId ?: return@launch
            val messageId = terminalLiveOutputMessageId ?: return@launch
            val content = terminalLiveOutput
            runCatching {
                callBridge { it.updateTerminalOutput(chatId, messageId, content, complete = false) }
            }
        }
    }

    private fun completeTerminalOutput(generation: Long, disconnected: Boolean) {
        if (generation != terminalGeneration) return
        terminalPersistJob?.cancel()
        terminalPersistJob = null
        val chatId = terminalChatId ?: return
        val messageId = terminalLiveOutputMessageId ?: return
        val content = terminalLiveOutput.ifBlank {
            if (disconnected) "（远程终端连接已结束）" else "（命令执行完成，没有输出）"
        }
        viewModelScope.launch {
            runCatching {
                callBridge { it.updateTerminalOutput(chatId, messageId, content, complete = true) }
                if (activeChat?.chat?.id == chatId) {
                    refreshActiveChat(chatId, activeChatGeneration)
                }
            }
            if (
                generation == terminalGeneration &&
                terminalLiveOutputMessageId == messageId
            ) {
                terminalLiveOutputMessageId = null
                terminalLiveOutput = ""
                terminalBuffer.clear()
            }
        }
    }

    private fun stopTerminalSession(persistOutput: Boolean = true) {
        val chatId = terminalChatId
        val messageId = terminalLiveOutputMessageId
        val content = terminalLiveOutput.ifBlank { "（远程终端会话已结束）" }
        terminalGeneration += 1
        terminalReaderJob?.cancel()
        terminalReaderJob = null
        terminalPersistJob?.cancel()
        terminalPersistJob = null
        terminalSession?.close()
        terminalSession = null
        tunnel.closeTerminal()
        terminalChatId = null
        terminalCompletionMarker = null
        terminalStatus = TerminalStatus.Disconnected
        terminalCommandSending = false
        terminalCommandRunning = false
        terminalLiveOutputMessageId = null
        terminalLiveOutput = ""
        terminalBuffer.clear()
        if (persistOutput && chatId != null && messageId != null && activeProfile != null) {
            viewModelScope.launch {
                runCatching {
                    callBridge { it.updateTerminalOutput(chatId, messageId, content, complete = true) }
                }
            }
        }
    }

    fun togglePinned() {
        val detail = activeChat ?: return
        val generation = activeChatGeneration
        viewModelScope.launch {
            runTask {
                callBridge { it.updateChat(detail.chat.id, pinned = !detail.chat.pinned) }
                refreshActiveChat(detail.chat.id, generation)
                refreshChatsInternal()
            }
        }
    }

    fun interrupt() {
        val chatId = activeChat?.chat?.id ?: return
        val generation = activeChatGeneration
        viewModelScope.launch {
            runCatching { callBridge { it.interrupt(chatId) } }
            refreshActiveChat(chatId, generation)
            startPolling(chatId, generation)
        }
    }

    fun resolveApproval(messageId: Long, allow: Boolean) {
        val chatId = activeChat?.chat?.id ?: return
        val generation = activeChatGeneration
        viewModelScope.launch {
            try {
                callBridge { it.resolveApproval(chatId, messageId, allow) }
                refreshActiveChat(chatId, generation)
                startPolling(chatId, generation)
            } catch (error: Throwable) {
                if (error !is CancellationException) errorMessage = error.userMessage()
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            runTask {
                if (terminalChatId == chatId) stopTerminalSession()
                callBridge { it.deleteChat(chatId) }
                pendingWebAttachments.remove(chatId)
                if (ocrPreviewTargetChatId == chatId) cancelOcrPreview()
                if (activeChat?.chat?.id == chatId) closeChat()
                refreshChatsInternal()
            }
        }
    }

    fun showFolderPicker(initialPath: String? = null) {
        viewModelScope.launch {
            runTask {
                folderPathSuggestions = emptyList()
                folderListing = callBridge { it.listDirectories(initialPath) }
                folderPickerVisible = true
            }
        }
    }

    fun browseFolder(path: String) {
        folderSuggestionJob?.cancel()
        folderPathSuggestions = emptyList()
        viewModelScope.launch {
            runTask {
                folderListing = callBridge { it.listDirectories(path) }
            }
        }
    }

    fun dismissFolderPicker() {
        folderSuggestionJob?.cancel()
        folderSuggestionJob = null
        folderPathSuggestions = emptyList()
        folderPickerVisible = false
    }

    fun suggestFolderPath(path: String) {
        folderSuggestionJob?.cancel()
        folderPathSuggestions = emptyList()
        val typed = path.trim()
        if (!typed.startsWith('/')) return
        folderSuggestionJob = viewModelScope.launch {
            delay(PATH_SUGGESTION_DEBOUNCE_MILLIS)
            val suggestions = runCatching {
                callBridge { it.directorySuggestions(typed) }
            }.getOrDefault(emptyList())
            if (isActive) folderPathSuggestions = suggestions
        }
    }

    fun clearFolderPathSuggestions() {
        folderSuggestionJob?.cancel()
        folderSuggestionJob = null
        folderPathSuggestions = emptyList()
    }

    fun dismissNewChatModePicker() {
        newChatFolder = null
    }

    fun createChatForSelectedFolder(mode: String) {
        val path = newChatFolder ?: return
        if (mode !in setOf("claude", "terminal")) return
        newChatFolder = null
        createChat(path, mode)
    }

    fun selectCurrentFolder() {
        val listing = folderListing ?: return
        val detail = activeChat
        folderPickerVisible = false
        if (detail == null) {
            newChatFolder = listing.path
        } else {
            if (detail.chat.mode == "terminal") stopTerminalSession()
            val generation = activeChatGeneration
            viewModelScope.launch {
                runTask {
                    callBridge { it.updateChat(detail.chat.id, projectPath = listing.path) }
                    refreshActiveChat(detail.chat.id, generation)
                    refreshChatsInternal()
                    if (detail.chat.mode == "terminal" && generation == activeChatGeneration) {
                        startTerminalSession(detail.chat.id, listing.path)
                    }
                }
            }
        }
    }

    fun attachWebPage(title: String, url: String, content: String) {
        val chat = activeChat?.chat ?: run {
            errorMessage = "请先打开一个对话，再附加网页 OCR"
            return
        }
        if (chat.mode != "claude") {
            errorMessage = "网页 OCR 只能附加到 Claude 对话"
            return
        }
        val chatId = chat.id
        val cleaned = content.trim().take(MAX_WEB_ATTACHMENT_CHARS)
        if (cleaned.isBlank()) {
            errorMessage = "这个页面没有提取到可附加的正文"
            return
        }
        pendingWebAttachments[chatId] = WebAttachment(title.ifBlank { "网页资料" }, url, cleaned)
        selectTab(MainTab.CHATS)
    }

    fun showOcrPreview(title: String, url: String, content: String) {
        val chat = activeChat?.chat ?: run {
            errorMessage = "请先打开一个对话，再使用 OCR 附加"
            return
        }
        if (chat.mode != "claude") {
            errorMessage = "网页 OCR 只能附加到 Claude 对话"
            return
        }
        val chatId = chat.id
        val cleaned = content.trim().take(MAX_WEB_ATTACHMENT_CHARS)
        if (cleaned.isBlank()) {
            errorMessage = "这个页面没有提取到可预览的正文"
            return
        }
        ocrPreviewTargetChatId = chatId
        ocrPreviewDraft = WebAttachment(title.ifBlank { "网页资料" }, url, cleaned)
    }

    fun updateOcrPreview(content: String) {
        val draft = ocrPreviewDraft ?: return
        ocrPreviewDraft = draft.copy(content = content.take(MAX_WEB_ATTACHMENT_CHARS))
    }

    fun confirmOcrPreview() {
        val draft = ocrPreviewDraft ?: return
        val targetChatId = ocrPreviewTargetChatId ?: run {
            ocrPreviewDraft = null
            errorMessage = "OCR 所属对话已关闭，请重新附加"
            return
        }
        val cleaned = draft.content.trim().take(MAX_WEB_ATTACHMENT_CHARS)
        if (cleaned.isBlank()) {
            errorMessage = "OCR 预览内容不能为空"
            return
        }
        pendingWebAttachments[targetChatId] = draft.copy(content = cleaned)
        ocrPreviewDraft = null
        ocrPreviewTargetChatId = null
        selectTab(MainTab.CHATS)
    }

    fun cancelOcrPreview() {
        ocrPreviewDraft = null
        ocrPreviewTargetChatId = null
    }

    fun removeWebAttachment() {
        activeChat?.chat?.id?.let(pendingWebAttachments::remove)
    }

    fun saveDeepSeekApiKey(value: String) {
        val profile = activeProfile ?: run {
            errorMessage = "请先连接服务器"
            return
        }
        val secret = value.trim().toByteArray(Charsets.UTF_8)
        if (secret.isEmpty()) {
            errorMessage = "请输入 DeepSeek API Key"
            return
        }
        try {
            vault.saveSecret(deepSeekSecretName(profile.id), secret)
            deepSeekConfigured = true
            refreshDeepSeekBalance()
        } finally {
            secret.fill(0)
        }
    }

    fun removeDeepSeekApiKey() {
        val profile = activeProfile ?: return
        vault.deleteSecret(deepSeekSecretName(profile.id))
        deepSeekConfigured = false
        deepSeekBalance = null
    }

    fun refreshDeepSeekBalance() {
        val profile = activeProfile ?: return
        val key = vault.loadSecret(deepSeekSecretName(profile.id)) ?: run {
            deepSeekConfigured = false
            errorMessage = "请先设置 DeepSeek API Key"
            return
        }
        viewModelScope.launch {
            deepSeekBusy = true
            try {
                deepSeekBalance = callBridge { it.deepSeekBalance(key) }
            } catch (error: Throwable) {
                if (error !is CancellationException) errorMessage = error.userMessage()
            } finally {
                key.fill(0)
                deepSeekBusy = false
            }
        }
    }

    fun loadArtifact(id: String) {
        if (artifactImages.containsKey(id)) return
        viewModelScope.launch {
            runCatching {
                callBridge {
                    val bytes = it.artifactBytes(id)
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size,
                    )
                }
            }.getOrNull()?.let { artifactImages[id] = it }
        }
    }

    fun artifactContentUrl(id: String): String? = api?.artifactContentUrl(id)

    fun refreshRemoteFiles() {
        browseRemoteFiles(remoteFileListing?.path)
    }

    fun browseRemoteFiles(path: String? = null) {
        if (remoteFilesBusy) return
        remoteSuggestionJob?.cancel()
        remotePathSuggestions = emptyList()
        viewModelScope.launch {
            remoteFilesBusy = true
            try {
                remoteFileListing = callBridge { it.fileListing(path) }
            } catch (error: Throwable) {
                if (error !is CancellationException) errorMessage = error.userMessage()
            } finally {
                remoteFilesBusy = false
            }
        }
    }

    fun suggestRemotePath(path: String) {
        remoteSuggestionJob?.cancel()
        remotePathSuggestions = emptyList()
        val typed = path.trim()
        if (!typed.startsWith('/')) return
        remoteSuggestionJob = viewModelScope.launch {
            delay(PATH_SUGGESTION_DEBOUNCE_MILLIS)
            val suggestions = runCatching {
                callBridge { it.directorySuggestions(typed) }
            }.getOrDefault(emptyList())
            if (isActive) remotePathSuggestions = suggestions
        }
    }

    fun clearRemotePathSuggestions() {
        remoteSuggestionJob?.cancel()
        remoteSuggestionJob = null
        remotePathSuggestions = emptyList()
    }

    fun openRemoteFile(entry: RemoteFileEntry) {
        if (entry.isDirectory) {
            browseRemoteFiles(entry.path)
            return
        }
        remoteFilePreview = entry
        remoteFilePreviewBitmap = null
        remoteFilePreviewText = null
        remoteFilePreviewError = null
        if (entry.mimeType.startsWith("video/")) {
            remoteFilePreviewBusy = false
            return
        }
        if (!entry.isPreviewableImage() && !entry.isPreviewableText()) {
            remoteFilePreviewBusy = false
            return
        }
        val previewPath = entry.path
        viewModelScope.launch {
            remoteFilePreviewBusy = true
            try {
                if (entry.isPreviewableImage()) {
                    val bytes = callBridge { it.fileBytes(previewPath) }
                    val bitmap = decodePreviewBitmap(bytes)
                        ?: throw IOException("这张图片的格式暂时无法预览")
                    if (remoteFilePreview?.path == previewPath) remoteFilePreviewBitmap = bitmap
                } else {
                    val bytes = callBridge { it.fileBytes(previewPath, MAX_TEXT_PREVIEW_BYTES) }
                    if (remoteFilePreview?.path == previewPath) {
                        remoteFilePreviewText = bytes.toString(Charsets.UTF_8)
                    }
                }
            } catch (error: Throwable) {
                if (error !is CancellationException && remoteFilePreview?.path == previewPath) {
                    remoteFilePreviewError = error.userMessage()
                }
            } finally {
                if (remoteFilePreview?.path == previewPath) remoteFilePreviewBusy = false
            }
        }
    }

    fun dismissRemoteFilePreview() {
        remoteFilePreview = null
        remoteFilePreviewBitmap = null
        remoteFilePreviewText = null
        remoteFilePreviewError = null
        remoteFilePreviewBusy = false
    }

    fun remoteFileContentUrl(path: String): String? = api?.fileContentUrl(path)

    fun startGpuMonitoring() {
        if (gpuPollJob?.isActive == true || activeProfile == null) return
        gpuPollJob = viewModelScope.launch {
            gpuBusy = gpuSnapshot == null
            while (isActive && selectedTab == MainTab.GPU && activeProfile != null) {
                try {
                    gpuSnapshot = callBridge { it.gpuStatus() }
                    gpuError = null
                } catch (error: Throwable) {
                    if (error !is CancellationException) gpuError = error.userMessage()
                } finally {
                    gpuBusy = false
                }
                delay(GPU_POLL_INTERVAL_MILLIS)
            }
        }
    }

    fun stopGpuMonitoring() {
        gpuPollJob?.cancel()
        gpuPollJob = null
        gpuBusy = false
    }

    fun refreshGpuStatus() {
        stopGpuMonitoring()
        startGpuMonitoring()
    }

    private suspend fun retryHealth(bridge: BridgeApi): com.mobileclaude.app.data.HealthInfo {
        var last: Throwable? = null
        val deadline = SystemClock.elapsedRealtime() + BRIDGE_READY_TIMEOUT_MILLIS
        do {
            try {
                return withContext(Dispatchers.IO) { bridge.health() }
            } catch (error: Throwable) {
                last = error
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                delay(minOf(HEALTH_RETRY_DELAY_MILLIS, remaining))
            }
        } while (SystemClock.elapsedRealtime() < deadline)
        throw last
    }

    private suspend fun verifyConnection(
        connection: TunnelConnection,
    ): Pair<BridgeApi, com.mobileclaude.app.data.HealthInfo> {
        val bridge = BridgeApi(connection.localPort)
        return try {
            bridge to retryHealth(bridge)
        } catch (error: Throwable) {
            tunnel.disconnect()
            throw error
        }
    }

    private suspend fun prepareForManualConnection() {
        connectionGeneration += 1
        reconnectJob?.cancelAndJoin()
        reconnectJob = null
        connectionHealthJob?.cancelAndJoin()
        connectionHealthJob = null
        networkValidationJob?.cancelAndJoin()
        networkValidationJob = null
        activeChatGeneration += 1
        chatLoadJob?.cancelAndJoin()
        chatLoadJob = null
        pollJob?.cancelAndJoin()
        pollJob = null
        stopGpuMonitoring()
        stopTerminalSession(persistOutput = false)
        tunnel.disconnect()
        api = null
        activeProfile = null
        activeChat = null
        pendingWebAttachments.clear()
        ocrPreviewTargetChatId = null
        ocrPreviewDraft = null
        chats.clear()
        artifactImages.clear()
        deepSeekBalance = null
        deepSeekConfigured = false
        deepSeekBusy = false
        gpuSnapshot = null
        gpuError = null
        newChatFolder = null
        folderSuggestionJob?.cancel()
        folderSuggestionJob = null
        folderPathSuggestions = emptyList()
        clearRemoteFileState()
    }

    private fun clearRemoteFileState() {
        remoteSuggestionJob?.cancel()
        remoteSuggestionJob = null
        remotePathSuggestions = emptyList()
        remoteFileListing = null
        remoteFilesBusy = false
        remoteFilePreview = null
        remoteFilePreviewBitmap = null
        remoteFilePreviewText = null
        remoteFilePreviewBusy = false
        remoteFilePreviewError = null
    }

    private suspend fun refreshChatsInternal() {
        val updated = callBridge { it.listChats() }
        chats.clear()
        chats.addAll(updated)
    }

    private fun beginChatSelection(): Long {
        stopTerminalSession()
        activeChatGeneration += 1
        pollJob?.cancel()
        pollJob = null
        activeChat = null
        return activeChatGeneration
    }

    private suspend fun openChatInternal(chatId: String, generation: Long) {
        val detail = callBridge { it.getChat(chatId) }
        if (generation != activeChatGeneration) return
        activeChat = detail
        selectedTab = MainTab.CHATS
        if (detail.chat.mode == "terminal") {
            startTerminalSession(detail.chat.id, detail.chat.projectPath)
        } else {
            startPolling(chatId, generation)
        }
    }

    private fun startPolling(chatId: String, generation: Long) {
        if (
            generation != activeChatGeneration ||
            selectedTab != MainTab.CHATS ||
            activeChat?.chat?.id != chatId ||
            activeChat?.chat?.status != "running"
        ) {
            return
        }
        // A callback from an old chat must not cancel the current chat's poller.
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (
                isActive &&
                generation == activeChatGeneration &&
                selectedTab == MainTab.CHATS &&
                activeChat?.chat?.id == chatId &&
                activeChat?.chat?.status == "running"
            ) {
                delay(CHAT_POLL_INTERVAL_MILLIS)
                runCatching { refreshActiveChat(chatId, generation) }
            }
        }
    }

    private suspend fun refreshActiveChat(chatId: String, generation: Long) {
        val detail = callBridge { it.getChat(chatId) }
        if (
            generation == activeChatGeneration &&
            activeChat?.chat?.id == chatId
        ) {
            activeChat = detail
        }
    }

    private suspend fun <T> callBridge(request: (BridgeApi) -> T): T {
        val bridge = awaitConnectedApi()
        return try {
            withContext(Dispatchers.IO) { request(bridge) }
        } catch (error: Throwable) {
            if (!error.isTunnelInterruption()) throw error
            if (api === bridge) api = null
            scheduleTunnelRecovery("连接暂时中断，正在自动恢复…")
            val recovered = waitForRecoveredApi()
                ?: throw IOException("连接正在后台自动恢复，请稍后重试", error)
            try {
                withContext(Dispatchers.IO) { request(recovered) }
            } catch (retryError: Throwable) {
                if (retryError.isTunnelInterruption()) {
                    if (api === recovered) api = null
                    scheduleTunnelRecovery("连接仍不稳定，正在继续自动恢复…")
                }
                throw retryError
            }
        }
    }

    private suspend fun awaitConnectedApi(): BridgeApi {
        api?.let { return it }
        if (activeProfile == null) throw IOException("请先连接服务器")
        scheduleTunnelRecovery("连接已中断，正在自动恢复…")
        return waitForRecoveredApi()
            ?: throw IOException("连接正在后台自动恢复，请稍后重试")
    }

    private suspend fun waitForRecoveredApi(): BridgeApi? = withTimeoutOrNull(
        REQUEST_RECOVERY_WAIT_MILLIS,
    ) {
        while (activeProfile != null) {
            api?.let { return@withTimeoutOrNull it }
            delay(RECOVERY_STATE_POLL_MILLIS)
        }
        null
    }

    private fun scheduleTunnelRecovery(message: String): Job? {
        val profile = activeProfile ?: return null
        reconnectJob?.takeIf { it.isActive }?.let { return it }
        val generation = connectionGeneration
        val job = viewModelScope.launch {
            var failureCount = 0
            try {
                while (
                    isActive &&
                    generation == connectionGeneration &&
                    activeProfile?.id == profile.id
                ) {
                    connectionStatus = ConnectionStatus.Connecting(
                        if (failureCount == 0) message
                        else "正在自动重连 ${profile.name}（第 ${failureCount + 1} 次）…",
                    )
                    val result = runCatching {
                        reconnectMutex.withLock {
                            if (
                                generation != connectionGeneration ||
                                activeProfile?.id != profile.id
                            ) {
                                throw CancellationException("连接目标已改变")
                            }
                            api?.let { candidate ->
                                val health = withContext(Dispatchers.IO) { candidate.health() }
                                return@withLock Triple(candidate, health, null as String?)
                            }
                            val connection = tunnel.connect(profile) { progress ->
                                connectionStatus = ConnectionStatus.Connecting(progress)
                            }
                            connectionStatus = ConnectionStatus.Connecting("等待服务器组件就绪…")
                            val (bridge, health) = verifyConnection(connection)
                            Triple(bridge, health, connection.bridgeWarning)
                        }
                    }
                    if (result.isSuccess) {
                        val (bridge, health, warning) = result.getOrThrow()
                        api = bridge
                        profileRepository.setLastConnectedProfileId(profile.id)
                        connectionStatus = ConnectionStatus.Connected(health)
                        warning?.let { errorMessage = it }
                        startConnectionHealthMonitor()
                        refreshAfterReconnect(bridge)
                        return@launch
                    }

                    val error = result.exceptionOrNull() ?: IOException("连接未完成")
                    if (error is CancellationException) throw error
                    api = null
                    tunnel.disconnect()
                    if (!error.isTunnelInterruption()) {
                        profileRepository.setLastConnectedProfileId(null)
                        connectionStatus = ConnectionStatus.Failed(error.userMessage())
                        return@launch
                    }
                    failureCount += 1
                    val retryDelay = ReconnectDelayPolicy.delayMillisAfterFailure(failureCount)
                    connectionStatus = ConnectionStatus.Connecting(
                        "VPN 或网络暂时不可用，${retryDelay / 1_000} 秒后自动重试…",
                    )
                    delay(retryDelay)
                }
            } finally {
                if (generation == connectionGeneration && reconnectJob === coroutineContext[Job]) {
                    reconnectJob = null
                }
            }
        }
        reconnectJob = job
        return job
    }

    private fun startConnectionHealthMonitor() {
        connectionHealthJob?.cancel()
        val generation = connectionGeneration
        connectionHealthJob = viewModelScope.launch {
            while (isActive && generation == connectionGeneration && activeProfile != null) {
                delay(CONNECTION_HEALTH_INTERVAL_MILLIS)
                val bridge = api
                if (bridge == null) {
                    scheduleTunnelRecovery("安全连接已断开，正在自动恢复…")
                    continue
                }
                try {
                    val health = withContext(Dispatchers.IO) { bridge.health() }
                    if (api === bridge) connectionStatus = ConnectionStatus.Connected(health)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    if (error.isTunnelInterruption() && api === bridge) {
                        api = null
                        scheduleTunnelRecovery("健康检查发现连接中断，正在自动恢复…")
                    }
                }
            }
        }
    }

    private suspend fun refreshAfterReconnect(bridge: BridgeApi) {
        runCatching {
            val updated = withContext(Dispatchers.IO) { bridge.listChats() }
            chats.clear()
            chats.addAll(updated)
        }
        val detail = activeChat ?: return
        val refreshed = runCatching {
            withContext(Dispatchers.IO) { bridge.getChat(detail.chat.id) }
        }.getOrNull()
        if (refreshed != null && activeChat?.chat?.id == detail.chat.id) {
            activeChat = refreshed
        }
        val current = activeChat ?: return
        if (current.chat.mode == "terminal") {
            startTerminalSession(current.chat.id, current.chat.projectPath)
        } else if (current.chat.status == "running") {
            startPolling(current.chat.id, activeChatGeneration)
        }
    }

    private fun refreshDeepSeekConfiguration(profile: ServerProfile) {
        deepSeekConfigured = vault.loadSecret(deepSeekSecretName(profile.id))?.also { it.fill(0) } != null
        deepSeekBalance = null
    }

    private fun deepSeekSecretName(profileId: String) = "deepseek_$profileId"

    private suspend fun runTask(block: suspend () -> Unit) {
        busyTaskCount += 1
        busy = true
        errorMessage = null
        try {
            block()
        } catch (error: Throwable) {
            if (error !is CancellationException) {
                errorMessage = error.userMessage()
                if (connectionStatus is ConnectionStatus.Connecting) {
                    connectionStatus = ConnectionStatus.Failed(error.userMessage())
                }
            }
        } finally {
            busyTaskCount = (busyTaskCount - 1).coerceAtLeast(0)
            busy = busyTaskCount > 0
        }
    }

    override fun onCleared() {
        connectionGeneration += 1
        reconnectJob?.cancel()
        connectionHealthJob?.cancel()
        networkValidationJob?.cancel()
        folderSuggestionJob?.cancel()
        remoteSuggestionJob?.cancel()
        if (networkCallbackRegistered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        stopGpuMonitoring()
        stopTerminalSession(persistOutput = false)
        tunnel.disconnect()
        super.onCleared()
    }
}

private const val CHAT_POLL_INTERVAL_MILLIS = 1_200L
private const val GPU_POLL_INTERVAL_MILLIS = 2_000L
private const val CONNECTION_HEALTH_INTERVAL_MILLIS = 8_000L
private const val NETWORK_SETTLE_DELAY_MILLIS = 900L
private const val REQUEST_RECOVERY_WAIT_MILLIS = 20_000L
private const val RECOVERY_STATE_POLL_MILLIS = 200L
private const val PATH_SUGGESTION_DEBOUNCE_MILLIS = 250L
private const val MAX_TEXT_PREVIEW_BYTES = 300_000
private const val MAX_IMAGE_PREVIEW_DIMENSION = 2_048
private const val MAX_TERMINAL_COMMAND_CHARS = 16_000
private const val TERMINAL_PERSIST_INTERVAL_MILLIS = 300L

private fun RemoteFileEntry.isPreviewableImage(): Boolean = mimeType.startsWith("image/") &&
    name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

private fun RemoteFileEntry.isPreviewableText(): Boolean = mimeType.startsWith("text/") ||
    name.substringAfterLast('.', "").lowercase() in setOf(
        "txt", "md", "log", "csv", "tsv", "json", "jsonl", "xml", "yaml", "yml",
        "py", "kt", "java", "js", "ts", "tsx", "jsx", "html", "css", "sh", "ini", "cfg",
    )

private fun decodePreviewBitmap(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > MAX_IMAGE_PREVIEW_DIMENSION ||
        bounds.outHeight / sampleSize > MAX_IMAGE_PREVIEW_DIMENSION
    ) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )
}

private fun Throwable.isTunnelInterruption(): Boolean {
    val causes = generateSequence(this) { it.cause }.toList()
    if (causes.any { cause ->
            cause is ConnectException ||
                cause is SocketTimeoutException ||
                cause is SocketException ||
                cause is NoRouteToHostException ||
                cause is EOFException ||
                cause is UnknownHostException
        }
    ) {
        return true
    }
    val details = causes.joinToString(" | ") { it.message.orEmpty() }.lowercase()
    return listOf(
        "timeout",
        "timed out",
        "connection refused",
        "connection reset",
        "socket is not established",
        "network is unreachable",
        "no route to host",
        "connection closed",
        "unexpected end of stream",
    ).any(details::contains)
}

private fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() }
    ?: "操作失败，请检查局域网和服务器状态"

private const val BRIDGE_READY_TIMEOUT_MILLIS = 100_000L
private const val HEALTH_RETRY_DELAY_MILLIS = 500L
