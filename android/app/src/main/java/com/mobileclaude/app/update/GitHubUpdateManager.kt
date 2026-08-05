package com.mobileclaude.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.mobileclaude.app.BuildConfig
import com.mobileclaude.app.data.AppUpdate
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class GitHubUpdateManager(private val context: Context) {
    fun isConfigured(): Boolean = REPOSITORY_PATTERN.matches(BuildConfig.GITHUB_REPOSITORY)

    fun checkForUpdate(): AppUpdate? {
        check(isConfigured()) { "GitHub 仓库地址尚未配置" }
        val manifestUrl =
            "https://github.com/${BuildConfig.GITHUB_REPOSITORY}/releases/latest/download/latest.json"
        val connection = openGet(manifestUrl)
        val text = try {
            if (connection.responseCode !in 200..299) {
                throw IOException("更新服务返回 ${connection.responseCode}")
            }
            val length = connection.contentLengthLong
            if (length > MAX_MANIFEST_BYTES) throw IOException("更新清单过大")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val value = reader.readText()
                if (value.toByteArray(Charsets.UTF_8).size > MAX_MANIFEST_BYTES) {
                    throw IOException("更新清单过大")
                }
                value
            }
        } finally {
            connection.disconnect()
        }
        val json = JSONObject(text)
        val update = AppUpdate(
            versionCode = json.getInt("versionCode"),
            versionName = json.getString("versionName").trim(),
            apkUrl = json.getString("apkUrl").trim(),
            sha256 = json.getString("sha256").trim().lowercase(),
            releaseNotes = json.optString("releaseNotes").trim(),
        )
        require(update.versionCode > 0 && update.versionName.isNotBlank()) { "更新清单版本无效" }
        require(SHA256_PATTERN.matches(update.sha256)) { "更新清单校验值无效" }
        require(isAllowedDownloadUrl(update.apkUrl)) { "更新下载地址不受信任" }
        return update.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    fun download(update: AppUpdate): File {
        val directory = context.getExternalFilesDir("updates")
            ?: throw IOException("无法创建更新目录")
        directory.mkdirs()
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.name != "ClaudeLink-${update.versionName}.apk") file.delete()
        }
        val target = File(directory, "ClaudeLink-${update.versionName}.apk")
        val partial = File(directory, "${target.name}.part")
        partial.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = openGet(update.apkUrl)
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("更新下载失败（${connection.responseCode}）")
            }
            val length = connection.contentLengthLong
            if (length > MAX_APK_BYTES) throw IOException("更新包超过大小限制")
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) throw IOException("更新包超过大小限制")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(update.sha256, ignoreCase = true)) {
            partial.delete()
            throw IOException("更新包 SHA-256 校验失败，已停止安装")
        }
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        return target
    }

    fun launchInstaller(apk: File): InstallLaunchResult {
        require(apk.isFile) { "更新包不存在" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return InstallLaunchResult.PermissionRequired
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(install)
        return InstallLaunchResult.Launched
    }

    private fun openGet(rawUrl: String): HttpURLConnection {
        var current = URI(rawUrl)
        repeat(MAX_REDIRECTS + 1) {
            require(current.scheme.equals("https", ignoreCase = true)) { "更新连接必须使用 HTTPS" }
            val connection = current.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 45_000
            connection.setRequestProperty("Accept", "application/json, application/octet-stream")
            connection.setRequestProperty("User-Agent", "ClaudeLink/${BuildConfig.VERSION_NAME}")
            val code = connection.responseCode
            if (code !in 300..399) return connection
            val location = connection.getHeaderField("Location")
                ?: throw IOException("更新服务重定向无效")
            connection.disconnect()
            current = current.resolve(location)
        }
        throw IOException("更新服务重定向次数过多")
    }

    private fun isAllowedDownloadUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.path.startsWith("/${BuildConfig.GITHUB_REPOSITORY}/releases/download/")
    }.getOrDefault(false)

    companion object {
        private const val MAX_REDIRECTS = 6
        private const val MAX_MANIFEST_BYTES = 512 * 1024
        private const val MAX_APK_BYTES = 250L * 1024 * 1024
        private val REPOSITORY_PATTERN = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
        private val SHA256_PATTERN = Regex("^[a-fA-F0-9]{64}$")
    }
}

enum class InstallLaunchResult { Launched, PermissionRequired }
