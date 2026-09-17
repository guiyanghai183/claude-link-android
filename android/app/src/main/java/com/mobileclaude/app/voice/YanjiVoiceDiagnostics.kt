package com.mobileclaude.app.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.mobileclaude.app.BuildConfig
import com.mobileclaude.app.MainActivity
import com.mobileclaude.app.R

/** Local, credential-free checks that separate the host notification bridge from direct APIs. */
internal object YanjiVoiceDiagnostics {
    private const val TEST_CHANNEL = "yanji_voice_diagnostic_notification"
    private const val TEST_NOTIFICATION = 1803
    private val handler = Handler(Looper.getMainLooper())
    private var pendingTest: Runnable? = null

    fun snapshot(context: Context): String = buildString {
        appendLine("Claude Link ${BuildConfig.VERSION_NAME}")
        appendLine("安卓环境：${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("震动权限：${context.checkSelfPermission(Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED}")
        appendLine(YanjiCallVibration.describe(context))
        runCatching {
            val audio = context.getSystemService(AudioManager::class.java)
            val modes = mapOf(AudioManager.RINGER_MODE_NORMAL to "响铃", AudioManager.RINGER_MODE_VIBRATE to "仅振动", AudioManager.RINGER_MODE_SILENT to "静音")
            appendLine("安卓声音模式：${modes[audio.ringerMode] ?: audio.ringerMode}")
            listOf("铃声" to AudioManager.STREAM_RING, "媒体" to AudioManager.STREAM_MUSIC, "通知" to AudioManager.STREAM_NOTIFICATION).forEach { (label, stream) ->
                appendLine("$label 音量：${audio.getStreamVolume(stream)}/${audio.getStreamMaxVolume(stream)}，静音=${audio.isStreamMute(stream)}")
            }
        }.onFailure { appendLine("读取音量失败：${it.javaClass.simpleName}") }
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            appendLine("安卓通知权限：${manager.areNotificationsEnabled()}，打扰过滤值=${manager.currentInterruptionFilter}")
            listOf("安卓来电渠道" to YanjiVoiceService.CALL_CHANNEL, "普通测试渠道" to TEST_CHANNEL).forEach { (name, id) ->
                val channel = manager.getNotificationChannel(id)
                if (channel == null) appendLine("$name：未创建")
                else appendLine("$name：级别=${channel.importance}，震动=${channel.shouldVibrate()}，已设声音=${channel.sound != null}")
            }
        }.onFailure { appendLine("读取通知设置失败：${it.javaClass.simpleName}") }
        append("以上为安卓环境报告，不代表鸿蒙手机已实际响铃或震动。")
    }

    fun scheduleNotification(context: Context, onUpdate: (String) -> Unit) {
        val app = context.applicationContext
        pendingTest?.let(handler::removeCallbacks)
        val task = Runnable {
            pendingTest = null
            val result = runCatching {
                val manager = app.getSystemService(NotificationManager::class.java)
                if (!manager.areNotificationsEnabled()) return@runCatching "测试通知未发送：安卓环境报告通知已关闭。"
                manager.createNotificationChannel(NotificationChannel(TEST_CHANNEL, "研记提醒测试", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 450, 350, 450)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                })
                val actualChannel = manager.getNotificationChannel(TEST_CHANNEL)
                val channelReport = "普通测试渠道：级别=${actualChannel.importance}，震动=${actualChannel.shouldVibrate()}，已设声音=${actualChannel.sound != null}。"
                if (actualChannel.importance == NotificationManager.IMPORTANCE_NONE)
                    return@runCatching "测试通知未发送：普通测试渠道已关闭。$channelReport"
                val open = PendingIntent.getActivity(app, TEST_NOTIFICATION, Intent(app, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.notify(TEST_NOTIFICATION, Notification.Builder(app, TEST_CHANNEL)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("研记提醒测试")
                    .setContentText("请确认这条通知是否有声音和震动。")
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(20_000).build())
                "已提交普通测试通知；本次没有调用直接铃声或震动接口，请记录手机实际反馈。$channelReport"
            }.getOrElse { "测试通知失败：${it.javaClass.simpleName}" }
            onUpdate(result)
        }
        pendingTest = task
        onUpdate("将在三秒后发送普通测试通知，可先回到手机桌面观察。")
        handler.postDelayed(task, 3_000)
    }
}
