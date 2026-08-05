package com.mobileclaude.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.webkit.WebView
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

data class OcrWebPage(val title: String, val url: String, val content: String)

suspend fun extractWebPageWithOcr(
    webView: WebView,
    fallbackTitle: String,
    fallbackUrl: String,
    pagesFromCurrent: Int? = null,
    onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
): OcrWebPage {
    val info = readPageInfo(webView)
    val title = info.optString("title", fallbackTitle).ifBlank { fallbackTitle }
    val url = info.optString("url", fallbackUrl).ifBlank { fallbackUrl }
    val originalY = info.optDouble("scrollY", 0.0)
    val scrollHeight = info.optDouble("scrollHeight", 0.0).coerceAtLeast(1.0)
    val viewportHeight = info.optDouble("viewportHeight", 0.0).coerceAtLeast(1.0)
    val readableText = info.optString("readableText").cleanWebText()
    val positions = capturePositions(originalY, scrollHeight, viewportHeight, pagesFromCurrent)
    val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val lines = LinkedHashSet<String>()
    var recognizedCharacters = readableText.length
    try {
        for ((index, y) in positions.withIndex()) {
            if (recognizedCharacters >= MAX_CONTEXT_CHARS) break
            onProgress(index + 1, positions.size)
            scrollPage(webView, y)
            // Dynamic documentation sites often repaint after a scroll.  Waiting a little
            // longer avoids taking a frame before its text has appeared.
            delay(if (index == 0) 420 else 260)
            val bitmap = captureVisibleWebView(webView)
            try {
                val input = InputImage.fromBitmap(bitmap, 0)
                val results = listOf(
                    chineseRecognizer.process(input).awaitResult(),
                    latinRecognizer.process(input).awaitResult(),
                )
                results.forEach { recognized ->
                    recognized.textBlocks
                        .flatMap { it.lines }
                        .map { line -> line.text.replace(Regex("\\s+"), " ").trim() }
                        .filter { it.isNotBlank() }
                        .forEach { line ->
                            if (
                                recognizedCharacters < MAX_CONTEXT_CHARS &&
                                line !in readableText &&
                                lines.add(line)
                            ) {
                                recognizedCharacters += line.length + 1
                            }
                        }
                    }
            } finally {
                bitmap.recycle()
            }
        }
    } finally {
        runCatching { scrollPage(webView, originalY) }
        chineseRecognizer.close()
        latinRecognizer.close()
    }
    val ocrText = lines.joinToString("\n")
    val mergedText = listOf(readableText, ocrText)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
        .take(MAX_CONTEXT_CHARS)
    if (mergedText.isBlank()) throw IOException("没有从当前网页提取到可附加文字")
    val notice = buildString {
        append("[以下内容由手机从当前浏览位置开始提取。优先读取网页可访问的正文，再以中文与英文双模型 OCR 补足画面文字；共扫描 ")
        append(positions.size)
        append(" 个页面片段]")
    }
    val content = "$notice\n\n$mergedText".take(MAX_CONTEXT_CHARS)
    return OcrWebPage(title, url, content)
}

private suspend fun readPageInfo(webView: WebView): JSONObject {
    val script = """
        (function() {
          var body = document.body || {};
          var root = document.documentElement || {};
          function cleanText(value) {
            return (value || '')
              .replace(/[ \t]+/g, ' ')
              .replace(/\n[ \t]+/g, '\n')
              .replace(/\n{3,}/g, '\n\n')
              .trim();
          }
          // Prefer the semantic article area.  Screenshot OCR is still used below for
          // canvas text and pages without usable DOM text, but documentation (including
          // SB3) can be transferred far more accurately this way.
          var source = document.querySelector('main, article, [role="main"], .rst-content, .document, .markdown-body, .md-content') || body;
          var clone = source.cloneNode(true);
          clone.querySelectorAll('script,style,noscript,svg,canvas,nav,header,footer,aside,[role="navigation"],.sidebar,.sphinxsidebar,.wy-nav-side,.toc,.table-of-contents').forEach(function(node) { node.remove(); });
          return JSON.stringify({
            title: document.title || '',
            url: location.href,
            scrollY: window.scrollY || root.scrollTop || 0,
            scrollHeight: Math.max(body.scrollHeight || 0, root.scrollHeight || 0),
            viewportHeight: window.innerHeight || root.clientHeight || 1,
            readableText: cleanText(clone.innerText).slice(0, ${MAX_DOM_CONTEXT_CHARS})
          });
        })();
    """.trimIndent()
    val encoded = webView.evaluate(script)
    val decoded = JSONTokener(encoded).nextValue() as? String
        ?: throw IOException("无法读取网页尺寸")
    return JSONObject(decoded)
}

private fun capturePositions(
    scrollY: Double,
    scrollHeight: Double,
    viewportHeight: Double,
    pagesFromCurrent: Int?,
): List<Double> {
    val maximum = (scrollHeight - viewportHeight).coerceAtLeast(0.0)
    val start = scrollY.coerceIn(0.0, maximum)
    if (maximum == 0.0 || start >= maximum) return listOf(start)
    val step = viewportHeight * 0.78
    if (pagesFromCurrent != null) {
        val requested = pagesFromCurrent.coerceIn(1, MAX_OCR_FRAMES)
        return List(requested) { index -> (start + index * step).coerceAtMost(maximum) }.distinct()
    }
    val estimatedFrames = ceil((maximum - start) / step).toInt().plus(1).coerceAtLeast(2)
    val count = min(MAX_OCR_FRAMES, estimatedFrames)
    return List(count) { index ->
        if (index == count - 1) maximum else start + (maximum - start) * index / (count - 1)
    }.distinct()
}

private suspend fun scrollPage(webView: WebView, y: Double) {
    webView.evaluate("window.scrollTo(0, ${y.roundToInt()}); null;")
}

private suspend fun captureVisibleWebView(webView: WebView): Bitmap =
    withContext(Dispatchers.Main.immediate) {
        val sourceWidth = webView.width
        val sourceHeight = webView.height
        if (sourceWidth <= 0 || sourceHeight <= 0) throw IOException("网页画面尚未准备好")
        // Upscaling preserves small documentation fonts for ML Kit.  Frames are handled
        // one at a time, so this is considerably more reliable than a low-resolution
        // screen capture while keeping memory bounded.
        val scale = min(MAX_CAPTURE_SCALE, MAX_CAPTURE_WIDTH.toFloat() / sourceWidth)
        val targetWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            Canvas(bitmap).apply {
                scale(scale, scale)
                webView.draw(this)
            }
        }
    }

private suspend fun WebView.evaluate(script: String): String =
    suspendCancellableCoroutine { continuation ->
        post {
            evaluateJavascript(script) { value ->
                if (continuation.isActive) continuation.resume(value ?: "null")
            }
        }
    }

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener { continuation.cancel() }
    }

private const val MAX_OCR_FRAMES = 24
private const val MAX_CAPTURE_WIDTH = 2048
private const val MAX_CAPTURE_SCALE = 2f
private const val MAX_CONTEXT_CHARS = 300_000
private const val MAX_DOM_CONTEXT_CHARS = 240_000

private fun String.cleanWebText(): String = replace(Regex("\\n{3,}"), "\n\n").trim()
