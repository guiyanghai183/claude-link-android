package com.mobileclaude.app.ssh

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.mobileclaude.app.R
import com.mobileclaude.app.data.ProfileRepository
import com.mobileclaude.app.data.ServerProfile
import com.mobileclaude.app.security.CredentialVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.UUID

data class TunnelConnection(
    val profile: ServerProfile,
    val localPort: Int,
    internal val session: Session,
    val bridgeWarning: String? = null,
)

class SshTunnelManager(
    private val context: Context,
    private val profiles: ProfileRepository,
    private val vault: CredentialVault,
) {
    private var active: TunnelConnection? = null

    suspend fun enroll(
        name: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        progress: (String) -> Unit,
    ): TunnelConnection = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "请输入服务器 IP" }
        require(username.isNotBlank()) { "请输入用户名" }
        require(password.isNotBlank()) { "首次连接需要服务器密码" }
        progress("验证服务器身份…")
        val jsch = configuredJsch()
        val normalizedHost = host.trim()
        val normalizedUsername = username.trim()
        val firstSession = connectSessionWithRetry(
            host = normalizedHost,
            port = port,
            authentication = Authentication.PASSWORD,
            progress = progress,
        ) { vpnNetwork ->
            jsch.getSession(normalizedUsername, normalizedHost, port).apply {
                setPassword(password)
                setConfig("StrictHostKeyChecking", "no")
                setConfig("PreferredAuthentications", "password,keyboard-interactive")
                configureTransport(this, vpnNetwork)
            }
        }
        var generatedPrivateKey: ByteArray? = null
        val enrollment = try {
            val hostKey = firstSession.hostKey.key
            val fingerprint = fingerprint(hostKey)

            progress("生成这台手机的专用密钥…")
            val pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 3072)
            val privateOutput = ByteArrayOutputStream()
            val publicOutput = ByteArrayOutputStream()
            pair.writePrivateKey(privateOutput)
            pair.writePublicKey(publicOutput, "claude-link-${UUID.randomUUID()}")
            pair.dispose()
            val privateKey = privateOutput.toByteArray().also { generatedPrivateKey = it }
            val publicKey = publicOutput.toString(Charsets.UTF_8.name()).trim()

            progress("启用免密码登录…")
            val encodedPublicKey = Base64.encodeToString(publicKey.toByteArray(), Base64.NO_WRAP)
            exec(
                firstSession,
                "umask 077; mkdir -p \"\$HOME/.ssh\"; touch \"\$HOME/.ssh/authorized_keys\"; " +
                    "PUB=\$(printf '%s' '$encodedPublicKey' | base64 -d); " +
                    "grep -qxF \"\$PUB\" \"\$HOME/.ssh/authorized_keys\" || printf '%s\\n' \"\$PUB\" >> \"\$HOME/.ssh/authorized_keys\"",
            )
            Triple(hostKey, fingerprint, privateKey)
        } catch (error: Throwable) {
            generatedPrivateKey?.fill(0)
            throw error
        } finally {
            firstSession.disconnect()
        }
        val (hostKey, fingerprint, privateKey) = enrollment

        val profile = ServerProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { normalizedHost },
            host = normalizedHost,
            port = port,
            username = normalizedUsername,
            hostKey = hostKey,
            fingerprint = fingerprint,
        )
        vault.savePrivateKey(profile.id, privateKey)
        profiles.save(profile)
        try {
            connectInternal(profile, progress)
        } catch (error: Throwable) {
            profiles.delete(profile.id)
            vault.delete(profile.id)
            throw error
        } finally {
            privateKey.fill(0)
        }
    }

    suspend fun connect(
        profile: ServerProfile,
        progress: (String) -> Unit,
    ): TunnelConnection = withContext(Dispatchers.IO) {
        connectInternal(profile, progress)
    }

    fun disconnect() {
        active?.session?.disconnect()
        active = null
    }

    private fun connectInternal(
        profile: ServerProfile,
        progress: (String) -> Unit,
    ): TunnelConnection {
        disconnect()
        val privateKey = vault.loadPrivateKey(profile.id)
            ?: error("未找到这台服务器的手机密钥，请删除服务器后重新添加")
        progress("建立加密连接…")
        val jsch = configuredJsch().apply {
            hostKeyRepository = PinnedHostKeyRepository(profile.hostKey)
            addIdentity(profile.id, privateKey, null, null)
        }
        privateKey.fill(0)
        val session = connectSessionWithRetry(
            host = profile.host,
            port = profile.port,
            authentication = Authentication.PUBLIC_KEY,
            progress = progress,
        ) { vpnNetwork ->
            jsch.getSession(profile.username, profile.host, profile.port).apply {
                setConfig("StrictHostKeyChecking", "yes")
                setConfig("PreferredAuthentications", "publickey")
                configureTransport(this, vpnNetwork)
            }
        }
        var localPort: Int? = null
        try {
            progress("打开安全通道…")
            localPort = session.setPortForwardingL(0, "127.0.0.1", REMOTE_PORT)
            progress("同步服务器组件…")
            val bridgeWarning = runCatching { bootstrapBridge(session, progress) }
                .exceptionOrNull()
                ?.let {
                    "已连接服务器，但组件自动更新未完成；当前会继续使用服务器上的已有版本。" +
                        it.message?.takeIf(String::isNotBlank)?.let { detail -> "（$detail）" }.orEmpty()
                }
            return TunnelConnection(profile, localPort, session, bridgeWarning).also { active = it }
        } catch (error: Throwable) {
            localPort?.let { runCatching { session.delPortForwardingL(it) } }
            session.disconnect()
            throw error
        }
    }

    private fun bootstrapBridge(session: Session, progress: (String) -> Unit) {
        val home = exec(session, "printf '%s' \"\$HOME\"").trim()
        require(home.startsWith('/')) { "无法确定服务器用户目录" }
        val installDir = "$home/.local/share/mobile-claude"
        val remotePath = "$installDir/server.py"
        val bridgeProcessPattern = "$installDir/[s]erver.py --host 127.0.0.1 --port $REMOTE_PORT"
        val bridgeBytes = context.resources.openRawResource(R.raw.mobile_claude_server).use { it.readBytes() }
        val localHash = sha256Hex(bridgeBytes)
        val remoteHash = exec(
            session,
            "test -f ${shellQuote(remotePath)} && sha256sum ${shellQuote(remotePath)} | cut -d' ' -f1 || true",
        ).trim()
        var bridgeUpdated = false
        if (remoteHash != localHash) {
            exec(session, "install -d -m 700 ${shellQuote(installDir)}")
            val temporary = "$remotePath.upload"
            val channel = session.openChannel("sftp") as ChannelSftp
            try {
                channel.connect(CHANNEL_CONNECT_TIMEOUT_MILLIS)
                ByteArrayInputStream(bridgeBytes).use { channel.put(it, temporary) }
                channel.chmod(448, temporary)
            } finally {
                channel.disconnect()
            }
            exec(
                session,
                "python3 -m py_compile ${shellQuote(temporary)} && mv ${shellQuote(temporary)} ${shellQuote(remotePath)}",
            )
            bridgeUpdated = true
        }
        if (bridgeUpdated) {
            progress("重启更新后的服务器组件…")
            exec(
                session,
                "if systemctl --user is-active --quiet mobile-claude.service; then " +
                    "systemctl --user restart --no-block mobile-claude.service; else " +
                    "pkill -f ${shellQuote(bridgeProcessPattern)} >/dev/null 2>&1 || true; " +
                    "for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do " +
                    "pgrep -f ${shellQuote(bridgeProcessPattern)} >/dev/null || break; sleep 0.1; done; " +
                    "pkill -KILL -f ${shellQuote(bridgeProcessPattern)} >/dev/null 2>&1 || true; fi",
            )
        }
        val launch =
            "pgrep -f ${shellQuote(bridgeProcessPattern)} >/dev/null || " +
                "nohup python3 ${shellQuote(remotePath)} --host 127.0.0.1 --port $REMOTE_PORT " +
                ">${shellQuote("$installDir/server.log")} 2>&1 </dev/null &"
        exec(session, launch, waitForExit = false)
    }

    private fun configuredJsch(): JSch {
        JSch.setConfig(
            "server_host_key",
            "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,rsa-sha2-512,rsa-sha2-256,ssh-ed25519",
        )
        return JSch()
    }

    private fun configureTransport(session: Session, vpnNetwork: Network?) {
        session.timeout = SOCKET_TIMEOUT_MILLIS
        session.serverAliveInterval = KEEPALIVE_INTERVAL_MILLIS
        session.serverAliveCountMax = KEEPALIVE_FAILURE_LIMIT
        vpnNetwork?.let { network ->
            session.setSocketFactory(AndroidNetworkSocketFactory(network, SOCKET_TIMEOUT_MILLIS))
        }
    }

    private fun preferredVpnNetwork(): Network? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val active = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(active) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
        val usable = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        return active.takeIf { usable }
    }

    private fun connectSessionWithRetry(
        host: String,
        port: Int,
        authentication: Authentication,
        progress: (String) -> Unit,
        createSession: (vpnNetwork: Network?) -> Session,
    ): Session {
        var lastError: Throwable? = null
        val enrollmentVpnNetwork = if (authentication == Authentication.PASSWORD) {
            preferredVpnNetwork()
        } else {
            null
        }
        repeat(CONNECTION_ATTEMPTS) { attempt ->
            // Prefer the active Android VPN while it is usable.  The last attempt
            // deliberately falls back to normal system routing so a stale or
            // split-tunnel VPN cannot make an otherwise reachable server unusable.
            // Password enrollment never changes routes mid-flight because the
            // server identity has not been pinned yet.
            val vpnNetwork = when (authentication) {
                Authentication.PASSWORD -> enrollmentVpnNetwork
                Authentication.PUBLIC_KEY ->
                    if (attempt < CONNECTION_ATTEMPTS - 1) preferredVpnNetwork() else null
            }
            val session = createSession(vpnNetwork)
            try {
                session.connect(CONNECT_TIMEOUT_MILLIS)
                return session
            } catch (error: Throwable) {
                session.disconnect()
                lastError = error
                val retry = attempt < CONNECTION_ATTEMPTS - 1 && error.isRetryableConnectionFailure()
                if (!retry) throw friendlyConnectionError(error, host, port, authentication)
                progress("网络暂时不稳定，正在重试（${attempt + 2}/$CONNECTION_ATTEMPTS）…")
                delayBlocking(RETRY_DELAYS_MILLIS[attempt])
            }
        }
        throw friendlyConnectionError(
            lastError ?: IOException("连接未完成"),
            host,
            port,
            authentication,
        )
    }

    private fun friendlyConnectionError(
        error: Throwable,
        host: String,
        port: Int,
        authentication: Authentication,
    ): IOException {
        val details = error.errorDetails().lowercase()
        val message = when {
            "hostkey" in details || "host key" in details ->
                "服务器身份指纹与手机保存的记录不一致。为保护账号，已拒绝连接"
            "auth fail" in details || "authentication" in details -> when (authentication) {
                Authentication.PASSWORD -> "SSH 登录失败，请检查服务器用户名和密码"
                Authentication.PUBLIC_KEY ->
                    "服务器拒绝了这台手机的密钥，请删除此服务器配置后重新添加"
            }
            error.isRetryableConnectionFailure() ->
                "无法连接 $host:$port。请确认手机 VPN 已连接、VPN 允许 Claude Link 访问服务器网段，且填写的是 SSH 端口（通常为 22）"
            else -> "SSH 连接失败：${error.message?.takeIf(String::isNotBlank) ?: "未知错误"}"
        }
        return IOException(message, error)
    }

    private fun exec(
        session: Session,
        command: String,
        waitForExit: Boolean = true,
        timeoutMillis: Long = COMMAND_TIMEOUT_MILLIS,
    ): String {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        channel.setInputStream(null)
        val errorOutput = ByteArrayOutputStream()
        channel.setErrStream(errorOutput)
        val output = channel.inputStream
        try {
            channel.connect(CHANNEL_CONNECT_TIMEOUT_MILLIS)
            if (!waitForExit) {
                delayBlocking(250)
                return ""
            }
            val standardOutput = ByteArrayOutputStream()
            val buffer = ByteArray(4_096)
            val deadline = System.nanoTime() + timeoutMillis * 1_000_000
            while (!channel.isClosed) {
                drainAvailable(output, standardOutput, buffer)
                if (System.nanoTime() >= deadline) {
                    throw IOException("服务器命令执行超时")
                }
                delayBlocking(20)
            }
            drainAvailable(output, standardOutput, buffer)
            val exit = channel.exitStatus
            val stdout = standardOutput.toString(Charsets.UTF_8.name())
            val stderr = errorOutput.toString(Charsets.UTF_8.name())
            if (exit != 0) {
                throw IOException(
                    stderr.trim().ifBlank { stdout.trim() }.ifBlank { "服务器命令执行失败（$exit）" },
                )
            }
            return stdout
        } finally {
            channel.disconnect()
        }
    }

    private fun drainAvailable(
        input: InputStream,
        output: ByteArrayOutputStream,
        buffer: ByteArray,
    ) {
        while (input.available() > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size, input.available()))
            if (count <= 0) return
            output.write(buffer, 0, count)
        }
    }

    private fun delayBlocking(milliseconds: Long) = Thread.sleep(milliseconds)

    private fun fingerprint(base64Key: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Base64.decode(base64Key, Base64.DEFAULT))
        return "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP).trimEnd('=')
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    companion object {
        const val REMOTE_PORT = 18_765
        private const val CONNECTION_ATTEMPTS = 3
        private const val CONNECT_TIMEOUT_MILLIS = 15_000
        private const val SOCKET_TIMEOUT_MILLIS = 12_000
        private const val CHANNEL_CONNECT_TIMEOUT_MILLIS = 12_000
        private const val COMMAND_TIMEOUT_MILLIS = 20_000L
        private const val KEEPALIVE_INTERVAL_MILLIS = 12_000
        private const val KEEPALIVE_FAILURE_LIMIT = 5
        private val RETRY_DELAYS_MILLIS = longArrayOf(800, 1_600)
    }
}

private enum class Authentication {
    PASSWORD,
    PUBLIC_KEY,
}

private class AndroidNetworkSocketFactory(
    private val network: Network,
    private val connectTimeoutMillis: Int,
) : com.jcraft.jsch.SocketFactory {
    override fun createSocket(host: String, port: Int): Socket {
        val addresses = network.getAllByName(host)
        var lastError: IOException? = null
        addresses.forEach { address ->
            val socket = network.socketFactory.createSocket()
            try {
                socket.connect(InetSocketAddress(address, port), connectTimeoutMillis)
                return socket
            } catch (error: IOException) {
                lastError = error
                runCatching { socket.close() }
            }
        }
        throw lastError ?: UnknownHostException(host)
    }

    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()

    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
}

private fun Throwable.errorDetails(): String = generateSequence(this) { it.cause }
    .joinToString(" | ") { it.message.orEmpty() }

private fun Throwable.isRetryableConnectionFailure(): Boolean {
    val causes = generateSequence(this) { it.cause }.toList()
    if (causes.any {
            it is ConnectException ||
                it is SocketTimeoutException ||
                it is SocketException ||
                it is NoRouteToHostException ||
                it is UnknownHostException ||
                it is EOFException
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
    ).any(details::contains)
}

private class PinnedHostKeyRepository(private val pinnedKey: String) : HostKeyRepository {
    override fun check(host: String?, key: ByteArray?): Int {
        if (key == null) return HostKeyRepository.NOT_INCLUDED
        val actual = Base64.encodeToString(key, Base64.NO_WRAP)
        return if (actual == pinnedKey) HostKeyRepository.OK else HostKeyRepository.CHANGED
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "Claude Link pinned host key"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}
