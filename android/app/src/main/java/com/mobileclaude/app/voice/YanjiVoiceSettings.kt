package com.mobileclaude.app.voice

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun YanjiVoiceSettings() {
    val context = LocalContext.current
    val config = remember { YanjiVoiceConfig(context) }
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(config.url) }
    var token by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(config.enabled) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (enabled) "已开启来电监听" else "尚未配对") }
    var fullScreenAllowed by remember { mutableStateOf(Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) }
    val fullScreenSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        fullScreenAllowed = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val channelSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        status = "已返回来电通知设置，可以进行提醒测试。"
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) status = "已配对，但需要通知权限才能显示来电"
    }

    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("研记来电", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text("让 ChatGPT 在需要确认实验细节时呼叫这台手机。配对码在研记的语音设置中生成，只能用于接听和回复。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("研记 HTTPS 地址") },
                placeholder = { Text("https://你的研记服务器") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(if (enabled) "新配对码（重新配对时填写）" else "配对码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    busy = true
                    status = "正在验证配对…"
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val cleanedUrl = YanjiVoiceConfig.validateUrl(url)
                                val cleanedToken = YanjiVoiceConfig.validateToken(token)
                                YanjiVoiceClient(cleanedUrl, cleanedToken).pending()
                                config.save(cleanedUrl, cleanedToken)
                            }
                        }.onSuccess {
                            token = ""
                            enabled = true
                            status = "已开启来电监听"
                            runCatching { YanjiVoiceService.start(context) }
                                .onFailure { status = "已配对，但来电监听未能启动：" + (it.message ?: "系统限制") }
                            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }.onFailure { status = it.message ?: "配对失败" }
                        busy = false
                    }
                },
                enabled = !busy && url.isNotBlank() && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (enabled) "重新配对" else "配对并开启来电") }
            if (enabled) {
                Spacer(Modifier.height(1.dp))
                YanjiAlertDiagnostics()
                OutlinedButton(onClick = {
                    runCatching {
                        channelSettings.launch(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, YanjiVoiceService.CALL_CHANNEL))
                    }.onFailure { status = "卓易通未提供来电通知渠道设置入口" }
                }, modifier = Modifier.fillMaxWidth()) { Text("打开安卓来电通知设置") }
                if (!fullScreenAllowed) {
                    Text("锁屏全屏来电权限尚未开启。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                fullScreenSettings.launch(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:${context.packageName}")))
                            }.onFailure { status = "卓易通未提供全屏来电设置入口，请检查系统通知设置" }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("开启锁屏全屏来电") }
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }.onFailure { status = "卓易通未提供通知设置入口" }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("检查来电铃声和通知") }
                OutlinedButton(
                    onClick = {
                        config.clear()
                        YanjiVoiceService.stop(context)
                        enabled = false
                        token = ""
                        status = "已关闭来电监听"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("关闭来电监听") }
            }
            Text("卓易通内还需开启本应用的通知铃声、振动和横幅通知。兼容播放测试使用媒体音量，测试结果以手机实际反馈为准。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun YanjiAlertDiagnostics() {
    val context = LocalContext.current
    val view = LocalView.current
    var log by rememberSaveable { mutableStateOf("") }
    var latest by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf(YanjiVoiceDiagnostics.snapshot(context)) }
    fun report(value: String) {
        latest = value
        log = (log + "\n" + value).takeLast(12_000)
        snapshot = YanjiVoiceDiagnostics.snapshot(context)
    }
    DisposableEffect(Unit) { onDispose { YanjiCallRingtone.stopTest() } }

    Text("来电兼容性检查", fontWeight = FontWeight.Bold)
    Text("通知测试会在三秒后发送普通提醒。下面两项铃声使用同一段内置音频，可对比来电与媒体播放是否有声。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = { YanjiVoiceDiagnostics.scheduleNotification(context, ::report) },
        modifier = Modifier.fillMaxWidth()) { Text("测试普通通知（声音与震动）") }
    OutlinedButton(onClick = { YanjiCallRingtone.test(context, false, ::report) },
        modifier = Modifier.fillMaxWidth()) { Text("测试内置来电铃声") }
    OutlinedButton(onClick = { YanjiCallRingtone.test(context, true, ::report) },
        modifier = Modifier.fillMaxWidth()) { Text("测试兼容铃声（媒体音量）") }
    OutlinedButton(onClick = { report(YanjiCallVibration.testWithReport(context)) },
        modifier = Modifier.fillMaxWidth()) { Text("测试直接震动") }
    if (latest.isNotBlank()) Text(latest, style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { details = !details; snapshot = YanjiVoiceDiagnostics.snapshot(context) },
        modifier = Modifier.fillMaxWidth()) { Text(if (details) "收起详细诊断" else "查看详细诊断") }
    if (details) {
        OutlinedButton(onClick = {
            val result = runCatching { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
            report(result.fold({ "按键触感请求返回 $it；是否实际震动以手机反馈为准。" },
                { "按键触感异常：${it.javaClass.simpleName}" }))
        }, modifier = Modifier.fillMaxWidth()) { Text("测试按键触感") }
        SelectionContainer { Text(snapshot + "\n\n测试记录：" + log, style = MaterialTheme.typography.bodySmall) }
    }
    OutlinedButton(onClick = {
        val text = YanjiVoiceDiagnostics.snapshot(context) + "\n\n测试记录：" + log
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("研记来电诊断", text))
        latest = "诊断结果已复制，可粘贴给我；不包含配对码或 API 密钥。"
    }, modifier = Modifier.fillMaxWidth()) { Text("复制诊断结果") }
}
