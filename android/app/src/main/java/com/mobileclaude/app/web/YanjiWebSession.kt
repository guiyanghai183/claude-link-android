package com.mobileclaude.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobileclaude.app.R
import org.json.JSONObject

/** Activity-owned WebView survives tab switches, retaining the live document and drafts. */
internal class YanjiWebSession(
    private val context: Context,
    private val savedHistory: Bundle,
) {
    private val preferences = context.getSharedPreferences("yanji_web", Context.MODE_PRIVATE)
    var baseUrl by mutableStateOf(
        runCatching {
            YanjiWebPolicy.normalizeUrl(preferences.getString("url", null) ?: YanjiWebPolicy.DEFAULT_URL)
        }.getOrDefault(YanjiWebPolicy.DEFAULT_URL)
    )
        private set
    var textZoom by mutableStateOf(
        YanjiWebPolicy.restoredTextZoom(preferences.getInt("text_zoom", YanjiWebPolicy.DEFAULT_TEXT_ZOOM))
    )
        private set
    var title by mutableStateOf("研记")
        private set
    var progress by mutableStateOf(0)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var launchFileChooser: ((Intent) -> Unit)? = null
    private var pendingFileChooser: ValueCallback<Array<Uri>>? = null
    private var visible = false
    private var foreground = true
    private var clearHistoryAfterLoad = false
    private var webView: WebView? = null
    private val popups = mutableSetOf<WebView>()
    private val mobileCss by lazy {
        context.resources.openRawResource(R.raw.yanji_mobile).bufferedReader().use { it.readText() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun view(): WebView {
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            return it
        }
        return WebView(context).apply {
            webView = this
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = false
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                defaultFontSize = 16
                minimumFontSize = 12
                textZoom = YanjiWebPolicy.effectiveTextZoom(
                    this@YanjiWebSession.textZoom, context.resources.configuration.fontScale,
                )
                allowFileAccess = false
                allowContentAccess = true // Selected documents supplied by the system file picker.
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(true)
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    if (request.isForMainFrame) routeNavigation(request.url.toString())
                    else !YanjiWebPolicy.isTrustedPage(request.url.toString(), baseUrl)

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    error = null
                    this@YanjiWebSession.progress = 0
                    canGoBack = view.canGoBack()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    this@YanjiWebSession.progress = 100
                    canGoBack = view.canGoBack()
                    if (clearHistoryAfterLoad) {
                        view.clearHistory()
                        canGoBack = false
                        clearHistoryAfterLoad = false
                    }
                    if (error == null && YanjiWebPolicy.isTrustedPage(url, baseUrl)) {
                        applyReadingStyles(view)
                        saveHistory()
                        CookieManager.getInstance().flush()
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    canGoBack = view.canGoBack()
                    saveHistory()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame) {
                        error = "研记暂时无法打开，请检查网络后重试。"
                        this@YanjiWebSession.progress = 100
                    }
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame && response.statusCode >= 400) {
                        error = "研记返回 " + response.statusCode + "，请稍后重试或检查网址。"
                        this@YanjiWebSession.progress = 100
                    }
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, failure: SslError) {
                    handler.cancel()
                    error = "研记网站的证书未通过验证，请检查网址或稍后重试。"
                    this@YanjiWebSession.progress = 100
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, value: Int) { this@YanjiWebSession.progress = value }
                override fun onReceivedTitle(view: WebView, value: String?) {
                    this@YanjiWebSession.title = value?.takeIf { it.isNotBlank() } ?: "研记"
                }

                override fun onShowFileChooser(
                    view: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams,
                ): Boolean {
                    if (!YanjiWebPolicy.isTrustedPage(view.url, baseUrl)) return false
                    completeFileChooser(null)
                    pendingFileChooser = callback
                    val launch = launchFileChooser
                    if (launch == null || runCatching { launch(params.createIntent()) }.isFailure) {
                        completeFileChooser(null)
                        message = "无法打开文件选择器，请在浏览器中上传附件。"
                    }
                    return true
                }

                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                    if (!isUserGesture || !YanjiWebPolicy.isTrustedPage(view.url, baseUrl)) return false
                    val popup = WebView(context)
                    popups.add(popup)
                    popup.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            if (YanjiWebPolicy.isTrustedPage(url, baseUrl)) webView?.loadUrl(url)
                            else openExternal(url)
                            closePopup(view)
                            return true
                        }
                    }
                    (resultMsg.obj as? WebView.WebViewTransport)?.let {
                        it.webView = popup
                        resultMsg.sendToTarget()
                        return true
                    }
                    closePopup(popup)
                    return false
                }
            }
            setDownloadListener { url, _, _, _, _ ->
                if (YanjiWebPolicy.isExternalWebUrl(url)) {
                    openExternal(url)
                } else {
                    message = "此文件请从菜单选择“在浏览器中打开”后下载。"
                }
            }
            val restored = if (savedHistory.isEmpty) null else runCatching { restoreState(savedHistory) }.getOrNull()
            if (restored == null || !YanjiWebPolicy.isTrustedPage(restored.currentItem?.url, baseUrl)) {
                savedHistory.clear()
                loadUrl(baseUrl)
            }
            updateLifecycle()
        }
    }

    private fun routeNavigation(url: String): Boolean {
        if (YanjiWebPolicy.isTrustedPage(url, baseUrl)) return false
        openExternal(url)
        return true
    }

    private fun applyReadingStyles(view: WebView) {
        // A DOM style only; there is no JavaScript bridge exposing Android APIs.
        val css = JSONObject.quote(mobileCss)
        view.evaluateJavascript(
            "(function(){var s=document.getElementById('claude-link-yanji-reading');" +
                "if(!s){s=document.createElement('style');s.id='claude-link-yanji-reading';" +
                "document.head.appendChild(s);}s.textContent=" + css + ";})();", null,
        )
    }

    fun changeTextZoom(value: Int) {
        textZoom = YanjiWebPolicy.restoredTextZoom(value)
        preferences.edit().putInt("text_zoom", textZoom).apply()
        webView?.settings?.textZoom = YanjiWebPolicy.effectiveTextZoom(
            textZoom, context.resources.configuration.fontScale,
        )
    }

    fun changeUrl(input: String) {
        val normalized = YanjiWebPolicy.normalizeUrl(input)
        if (normalized == baseUrl) return
        completeFileChooser(null)
        baseUrl = normalized
        preferences.edit().putString("url", normalized).apply()
        savedHistory.clear()
        error = null
        canGoBack = false
        clearHistoryAfterLoad = true
        webView?.stopLoading()
        webView?.loadUrl(baseUrl)
    }

    fun home() { error = null; webView?.loadUrl(baseUrl) }
    fun reload() { error = null; webView?.reload() }
    fun goBack() { if (webView?.canGoBack() == true) webView?.goBack() }
    fun openInBrowser() {
        openExternal(webView?.url?.takeIf { YanjiWebPolicy.isTrustedPage(it, baseUrl) } ?: baseUrl)
    }
    fun clearMessage() { message = null }

    fun completeFileChooser(uris: Array<Uri>?) {
        pendingFileChooser?.onReceiveValue(uris)
        pendingFileChooser = null
    }

    fun setVisible(value: Boolean) { visible = value; updateLifecycle() }
    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) { saveHistory(); CookieManager.getInstance().flush() }
        updateLifecycle()
    }

    private fun updateLifecycle() {
        webView?.let { if (visible && foreground) it.onResume() else it.onPause() }
    }

    fun saveHistory() {
        webView?.takeIf { YanjiWebPolicy.isTrustedPage(it.url, baseUrl) }?.saveState(savedHistory)
    }

    private fun closePopup(popup: WebView) {
        popup.stopLoading()
        popup.post { if (popups.remove(popup)) popup.destroy() }
    }

    private fun openExternal(url: String) {
        if (!YanjiWebPolicy.isExternalWebUrl(url)) {
            message = "此链接无法在应用中打开。"
            return
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { message = "无法打开浏览器，请检查手机的浏览器设置。" }
    }

    fun release() {
        saveHistory()
        completeFileChooser(null)
        launchFileChooser = null
        popups.toList().forEach { it.destroy() }
        popups.clear()
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        webView = null
    }
}
