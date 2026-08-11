package com.mobileclaude.app.ui

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputControllerTest {
    @Test
    fun recognizedSpeechAppendsWithoutBreakingExistingText() {
        assertEquals("你好", appendRecognizedSpeech("", "你好"))
        assertEquals("请检查 服务器状态", appendRecognizedSpeech("请检查", "服务器状态"))
        assertEquals("请检查 服务器状态", appendRecognizedSpeech("请检查 ", "服务器状态"))
    }

    @Test
    fun networkFailureExplainsTheOfflineAlternative() {
        assertTrue(
            speechErrorMessage(SpeechRecognizer.ERROR_NETWORK).contains("离线语音识别包")
        )
    }
}
