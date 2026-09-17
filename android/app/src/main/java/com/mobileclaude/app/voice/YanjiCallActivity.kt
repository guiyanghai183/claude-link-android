package com.mobileclaude.app.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobileclaude.app.ui.ClaudeLinkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class YanjiCallActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var call by mutableStateOf<YanjiCall?>(null)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf("")
    private var heard by mutableStateOf(false)
    private var lastTranscript by mutableStateOf("")
    private var callId = ""
    private lateinit var api: YanjiVoiceClient
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var sender: Job? = null
    private var receiver: Job? = null
    private var finished = false
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) answer() else message = "需要麦克风权限才能进行实时语音问答"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        callId = intent.getStringExtra(YanjiVoiceService.EXTRA_CALL_ID).orEmpty()
        val config = YanjiVoiceConfig(this)
        if (callId.isBlank() || !config.enabled) { finish(); return }
        api = YanjiVoiceClient(config.url, config.token())
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.SIMPLIFIED_CHINESE
                ttsReady = true
                if (call?.state == "connected") speakQuestion()
            } else message = "手机语音引擎不可用，请阅读屏幕上的问题"
        }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.status(callId) } }
                .onSuccess { call = it }
                .onFailure { message = it.message ?: "读取来电失败" }
        }
        setContent {
            ClaudeLinkTheme {
                val current = call
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("ChatGPT · 研记", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(if (current?.state == "ringing") "语音确认来电" else if (current?.active == true) "正在通话" else "通话已结束",
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(22.dp))
                    Text(current?.question ?: "正在读取问题…", style = MaterialTheme.typography.bodyLarge)
                    if (lastTranscript.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text("识别到：$lastTranscript")
                    }
                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(30.dp))
                    when (current?.state) {
                        "ringing" -> {
                            Button(onClick = { requestAnswer() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("接听") }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { decline() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("拒接") }
                        }
                        "connected" -> {
                            if (current.mode == "test") {
                                Button(onClick = { reply("已接到来电，声音清楚") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("已接到，声音清楚") }
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(onClick = { reply("已接到来电，但声音不清楚") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("已接到，声音不清楚") }
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(onClick = { speakQuestion() },
                                    modifier = Modifier.fillMaxWidth()) { Text("再听一遍问题") }
                            } else if (!heard) {
                                Button(onClick = { startAudio() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("开始说话") }
                            } else {
                                Text("正在传输语音，可直接说话", color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(18.dp))
                            OutlinedButton(onClick = { hangUp() }, modifier = Modifier.fillMaxWidth()) { Text("挂断") }
                        }
                        else -> {
                            Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("返回 Claude Link") }
                        }
                    }
                }
            }
        }
    }

    private fun requestAnswer() {
        if (call?.mode != "test" && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else answer()
    }

    private fun answer() {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.answer(callId) } }
                .onSuccess {
                    call = it
                    speakQuestion()
                    message = if (it.mode == "test") "听完问题后请选择声音是否清楚" else "听完问题后点击开始说话"
                }
                .onFailure { message = it.message ?: "接听失败" }
            busy = false
        }
    }

    private fun speakQuestion() {
        val question = call?.question ?: return
        if (ttsReady) tts?.speak(question, TextToSpeech.QUEUE_FLUSH, null, "yanji-question")
    }

    private fun startAudio() {
        if (heard) return
        val minRecord = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val minPlay = AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minRecord < 0 || minPlay < 0) { message = "手机音频设备不可用"; return }
        try {
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16_000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minRecord, 12_800))
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24_000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minPlay, 24_000))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            record.startRecording()
            track.play()
            audioRecord = record
            audioTrack = track
            heard = true
            message = ""
            sender = scope.launch(Dispatchers.IO) {
                try {
                    val buffer = ByteArray(6_400)
                    while (isActive) {
                        val count = record.read(buffer, 0, buffer.size)
                        if (count > 1) api.audioIn(callId, buffer.copyOf(count - count % 2))
                        else if (count < 0) throw IllegalStateException("麦克风读取失败")
                    }
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) { message = error.message ?: "麦克风传输中断"; stopAudio() }
                }
            }
            receiver = scope.launch(Dispatchers.IO) {
                var after = 0
                try {
                    while (isActive) {
                        val packet = api.events(callId, after)
                        val state = YanjiCall.fromJson(packet.getJSONObject("call"))
                        withContext(Dispatchers.Main) { call = state }
                        if (!state.active) break
                        val events = packet.getJSONArray("events")
                        for (index in 0 until events.length()) {
                            val event = events.getJSONObject(index)
                            after = event.getInt("seq")
                            when (event.optString("type")) {
                                "audio" -> {
                                    val audio = Base64.decode(event.getString("audio"), Base64.DEFAULT)
                                    var written = 0
                                    while (written < audio.size) {
                                        val count = track.write(audio, written, audio.size - written)
                                        if (count <= 0) throw IllegalStateException("扬声器播放中断")
                                        written += count
                                    }
                                }
                                "user" -> withContext(Dispatchers.Main) { lastTranscript = event.optString("text") }
                                "error" -> withContext(Dispatchers.Main) { message = event.optString("text") }
                            }
                        }
                        delay(220)
                    }
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) { message = error.message ?: "语音接收中断"; stopAudio() }
                }
            }
        } catch (error: Exception) {
            stopAudio()
            message = error.message ?: "无法启动麦克风"
        }
    }

    private fun stopAudio() {
        sender?.cancel()
        receiver?.cancel()
        sender = null
        receiver = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioRecord = null
        audioTrack = null
    }

    private fun reply(text: String) {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.reply(callId, text) } }
                .onSuccess { call = it; finished = true; tts?.stop() }
                .onFailure { message = it.message ?: "提交回答失败" }
            busy = false
        }
    }

    private fun decline() {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.decline(callId) } }
                .onSuccess { call = it; finished = true }
                .onFailure { message = it.message ?: "拒接失败" }
            busy = false
        }
    }

    private fun hangUp() {
        stopAudio()
        tts?.stop()
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.finish(callId) } }
                .onSuccess { call = it; finished = true }
                .onFailure { message = it.message ?: "挂断失败" }
            busy = false
        }
    }

    override fun onDestroy() {
        stopAudio()
        tts?.shutdown()
        if (!finished && call?.state == "connected") CoroutineScope(Dispatchers.IO).launch { runCatching { api.finish(callId) } }
        scope.cancel()
        super.onDestroy()
    }
}
