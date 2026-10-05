package com.mobileclaude.app.web

import java.net.URI
import kotlin.math.roundToInt

internal object YanjiWebPolicy {
    const val DEFAULT_URL = "https://8.133.186.127/yanji/"
    const val DEFAULT_TEXT_ZOOM = 110
    val textZoomOptions = listOf(100, 110, 125, 140, 160)

    fun normalizeUrl(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty()) { "请填写研记网址" }
        val candidate = if (value.contains("://")) value else "https://" + value
        val uri = runCatching { URI(candidate) }.getOrNull()
        require(uri != null && uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null &&
            (uri.port == -1 || uri.port in 1..65535) &&
            uri.rawQuery == null && uri.rawFragment == null
        ) { "请填写不含账号、参数或片段的 HTTPS 研记网址" }
        val path = when {
            uri.host == "8.133.186.127" && uri.path.orEmpty() in listOf("", "/") -> "/yanji/"
            uri.path.isNullOrEmpty() -> "/"
            else -> uri.path
        }
        return URI("https", null, uri.host.lowercase(), uri.port, path, null, null).toASCIIString()
    }

    fun isTrustedPage(url: String?, baseUrl: String): Boolean {
        val target = url?.let { runCatching { URI(it) }.getOrNull() } ?: return false
        val base = runCatching { URI(baseUrl) }.getOrNull() ?: return false
        return target.scheme.equals("https", ignoreCase = true) &&
            target.userInfo == null && !target.host.isNullOrBlank() &&
            target.host.equals(base.host, ignoreCase = true) &&
            effectivePort(target) == effectivePort(base)
    }

    fun isExternalWebUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null &&
            (uri.port == -1 || uri.port in 1..65535)
    }

    fun restoredTextZoom(value: Int): Int =
        value.takeIf { it in textZoomOptions } ?: DEFAULT_TEXT_ZOOM

    fun effectiveTextZoom(value: Int, systemFontScale: Float): Int =
        (restoredTextZoom(value) * systemFontScale).roundToInt().coerceIn(100, 240)

    private fun effectivePort(uri: URI) = if (uri.port == -1) 443 else uri.port
}
