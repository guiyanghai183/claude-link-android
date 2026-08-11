package com.mobileclaude.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Runs speech-to-text inside Claude Link instead of opening an external
 * recognition activity. Android 12+ uses the system on-device recognizer when
 * one is installed; older/default recognizers are explicitly asked to stay
 * offline.
 */
internal class VoiceInputController(context: Context) : RecognitionListener {
    private val appContext = context.applicationContext
    private val onDeviceRecognizerAvailable =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
    private val recognizerAndMode: Pair<SpeechRecognizer, Boolean>? =
        if (onDeviceRecognizerAvailable) {
            runCatching {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext) to true
            }.getOrNull()
        } else {
            null
        } ?: if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) to false }.getOrNull()
        } else {
            null
        }
    private val recognizer: SpeechRecognizer? =
        recognizerAndMode?.first?.also { it.setRecognitionListener(this) }

    val usesOnDeviceRecognizer: Boolean
        get() = recognizerAndMode?.second == true

    val available: Boolean
        get() = recognizer != null

    private var listening = false
    private var destroyed = false
    private var suppressClientError = false
    private var onResult: (String) -> Unit = {}
    private var onListeningChanged: (Boolean) -> Unit = {}
    private var onErrorMessage: (String) -> Unit = {}

    fun updateCallbacks(
        onResult: (String) -> Unit,
        onListeningChanged: (Boolean) -> Unit,
        onErrorMessage: (String) -> Unit,
    ) {
        this.onResult = onResult
        this.onListeningChanged = onListeningChanged
        this.onErrorMessage = onErrorMessage
    }

    fun start() {
        val activeRecognizer = recognizer ?: run {
            onErrorMessage("这台手机没有可用的系统语音识别服务，可使用输入法自带的语音输入")
            return
        }
        if (destroyed || listening) return
        suppressClientError = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            setListening(true)
            activeRecognizer.startListening(intent)
        } catch (error: SecurityException) {
            setListening(false)
            onErrorMessage("Claude Link 没有麦克风权限")
        } catch (error: Throwable) {
            setListening(false)
            onErrorMessage(error.message?.takeIf(String::isNotBlank) ?: "无法启动系统语音识别")
        }
    }

    fun stop() {
        if (!listening || destroyed) return
        runCatching { recognizer?.stopListening() }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        suppressClientError = true
        listening = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        onResult = {}
        onListeningChanged = {}
        onErrorMessage = {}
    }

    override fun onReadyForSpeech(params: Bundle?) = setListening(true)

    override fun onResults(results: Bundle?) {
        setListening(false)
        val spoken = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (spoken.isNotBlank()) onResult(spoken) else onErrorMessage("没有识别到语音内容")
    }

    override fun onError(error: Int) {
        setListening(false)
        if (suppressClientError && error == SpeechRecognizer.ERROR_CLIENT) return
        onErrorMessage(speechErrorMessage(error))
    }

    override fun onBeginningOfSpeech() = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit

    private fun setListening(value: Boolean) {
        if (listening == value || destroyed) return
        listening = value
        onListeningChanged(value)
    }
}

internal fun appendRecognizedSpeech(existing: String, spoken: String): String = when {
    existing.isBlank() -> spoken
    existing.lastOrNull()?.isWhitespace() == true -> existing + spoken
    else -> "$existing $spoken"
}

internal fun speechErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    -> "当前系统语音服务仍依赖网络。请安装或启用系统离线语音识别包，也可以使用输入法自带的语音输入"

    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Claude Link 没有麦克风权限"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    -> "系统没有当前语言的离线语音模型，请先在系统语音设置中下载"

    SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，请再说一次"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别正在忙，请稍后再试"
    SpeechRecognizer.ERROR_AUDIO -> "无法读取麦克风音频"
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    -> "系统语音识别服务暂时不可用"

    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "语音识别请求过于频繁，请稍后再试"
    SpeechRecognizer.ERROR_CLIENT -> "语音识别启动失败，请重试"
    else -> "语音识别失败（错误码 $error）"
}
