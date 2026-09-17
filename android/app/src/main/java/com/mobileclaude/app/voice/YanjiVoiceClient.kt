package com.mobileclaude.app.voice

import android.content.Context
import android.util.Base64
import com.mobileclaude.app.security.CredentialVault
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class YanjiCall(
    val id: String,
    val question: String,
    val state: String,
    val mode: String,
    val reply: String = "",
    val error: String = "",
) {
    val active: Boolean get() = state == "ringing" || state == "connecting" || state == "connected"
    companion object {
        fun fromJson(data: JSONObject) = YanjiCall(
            data.getString("id"),
            data.optString("question"),
            data.optString("state"),
            data.optString("mode"),
            data.optString("reply"),
            data.optString("error"),
        )
    }
}

class YanjiVoiceConfig(context: Context) {
    private val prefs = context.getSharedPreferences("yanji_voice", Context.MODE_PRIVATE)
    private val vault = CredentialVault(context)
    var url: String
        get() = prefs.getString("url", "").orEmpty()
        private set(value) { prefs.edit().putString("url", value).apply() }
    val enabled: Boolean get() = prefs.getBoolean("enabled", false)
    fun token(): String = vault.loadSecret("yanji_voice_device")?.toString(Charsets.UTF_8).orEmpty()
    fun save(rawUrl: String, rawToken: String) {
        val normalized = validateUrl(rawUrl)
        val token = validateToken(rawToken)
        url = normalized
        vault.saveSecret("yanji_voice_device", token.toByteArray(Charsets.UTF_8))
        prefs.edit().putBoolean("enabled", true).apply()
    }
    fun clear() {
        prefs.edit().remove("url").putBoolean("enabled", false).apply()
        vault.deleteSecret("yanji_voice_device")
    }
    companion object {
        fun validateUrl(rawUrl: String): String {
            val parsed = URI(rawUrl.trim().trimEnd('/'))
            require(parsed.scheme == "https" && !parsed.host.isNullOrBlank() && parsed.rawUserInfo == null &&
                parsed.rawQuery == null && parsed.rawFragment == null && (parsed.path.isNullOrBlank() || parsed.path == "/")) {
                "请填写研记的 HTTPS 服务器地址"
            }
            return parsed.toString().trimEnd('/')
        }
        fun validateToken(rawToken: String): String {
            val token = rawToken.trim()
            require(Regex("^yjv_[A-Za-z0-9_-]{40,100}$").matches(token)) { "配对码格式不正确" }
            return token
        }
    }
}

class YanjiVoiceClient(private val url: String, private val token: String) {
    init {
        YanjiVoiceConfig.validateUrl(url)
        YanjiVoiceConfig.validateToken(token)
    }
    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val connection = URL(url + path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Voice $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val content = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                val detail = runCatching { JSONObject(content).optString("error") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "研记请求失败 (" + connection.responseCode + ")")
            }
            return JSONObject(content)
        } finally {
            connection.disconnect()
        }
    }
    fun pending(): YanjiCall? = request("/api/voice/device/pending").optJSONObject("call")?.let(YanjiCall::fromJson)
    fun status(id: String): YanjiCall = YanjiCall.fromJson(request("/api/voice/device/calls/$id/status"))
    fun answer(id: String): YanjiCall = YanjiCall.fromJson(request("/api/voice/device/calls/$id/answer", "POST", JSONObject()))
    fun decline(id: String): YanjiCall = YanjiCall.fromJson(request("/api/voice/device/calls/$id/decline", "POST", JSONObject()))
    fun finish(id: String): YanjiCall = YanjiCall.fromJson(request("/api/voice/device/calls/$id/finish", "POST", JSONObject()))
    fun reply(id: String, text: String): YanjiCall = YanjiCall.fromJson(request("/api/voice/device/calls/$id/reply", "POST", JSONObject().put("text", text)))
    fun audioIn(id: String, pcm: ByteArray) {
        request("/api/voice/device/calls/$id/audio-in", "POST",
            JSONObject().put("audio", Base64.encodeToString(pcm, Base64.NO_WRAP)))
    }
    fun events(id: String, after: Int): JSONObject = request("/api/voice/device/calls/$id/events?after=$after")
}
