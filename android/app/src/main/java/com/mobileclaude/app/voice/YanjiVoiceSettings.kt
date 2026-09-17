package com.mobileclaude.app.voice

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    var vibrationStatus by remember { mutableStateOf("") }
    var fullScreenAllowed by remember { mutableStateOf(Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) }
    val fullScreenSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        fullScreenAllowed = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val channelSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vibrationStatus = "已返回来电通知设置，请用上方按钮测试。"
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
                val callChannel = context.getSystemService(NotificationManager::class.java)
                    .getNotificationChannel(YanjiVoiceService.CALL_CHANNEL)
                Text(
                    when {
                        callChannel == null -> "来电通知渠道尚未创建，请重新打开来电监听。"
                        !callChannel.shouldVibrate() -> "系统已关闭「研记来电」通知的震动。"
                        !YanjiCallVibration.channelAllowsVibration(context) -> "当前通知或免打扰设置阻止来电震动。"
                        else -> "已请求来电震动；卓易通是否传递到手机，请用下方按钮测试。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (vibrationStatus.isNotBlank()) Text(vibrationStatus, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    vibrationStatus = if (YanjiCallVibration.testOnce(context))
                        "已请求手机震动一次，请以手机实际反馈为准。"
                    else "当前安卓环境没有提供可用的震动设备。"
                }, modifier = Modifier.fillMaxWidth()) { Text("测试手机震动") }
                Text(
                    when {
                        callChannel?.sound == null -> "「研记来电」通知渠道没有设置铃声。"
                        context.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL ->
                            "手机当前为静音或仅震动模式，来电铃声不会响起。"
                        else -> "来电时会持续请求播放手机的来电铃声，接听或拒接后停止。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    vibrationStatus = if (YanjiCallRingtone.testOnce(context))
                        "已请求播放三秒来电铃声，请以手机实际声音为准。"
                    else "无法播放来电铃声：请检查声音模式、免打扰和来电通知设置。"
                }, modifier = Modifier.fillMaxWidth()) { Text("测试来电铃声") }
                OutlinedButton(onClick = {
                    runCatching {
                        channelSettings.launch(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, YanjiVoiceService.CALL_CHANNEL))
                    }.onFailure { status = "卓易通未提供来电通知渠道设置入口" }
                }, modifier = Modifier.fillMaxWidth()) { Text("设置研记来电震动") }
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
            Text("来电铃声和震动由来电监听主动请求；解锁使用手机时，系统通常先显示带接听按钮的来电横幅。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
