package com.mobileclaude.app.voice

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Request call vibration directly when a notification is shown. Some Android containers display
 * the call notification without forwarding its channel vibration to the physical phone. */
internal object YanjiCallVibration {
    private fun vibrator(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun channelAllowsVibration(context: Context): Boolean {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val channel = notifications.getNotificationChannel(YanjiVoiceService.CALL_CHANNEL)
        return notifications.areNotificationsEnabled() && channel != null &&
            channel.importance != NotificationManager.IMPORTANCE_NONE && channel.shouldVibrate() &&
            context.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_SILENT &&
            notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE
    }

    private fun request(device: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33) {
            device.vibrate(effect, VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_RINGTONE).build())
        } else {
            @Suppress("DEPRECATION")
            device.vibrate(effect, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
        }
    }

    fun start(context: Context): Boolean = runCatching {
        if (!channelAllowsVibration(context)) return false
        val device = vibrator(context) ?: return false
        if (!device.hasVibrator()) return false
        request(device, VibrationEffect.createWaveform(longArrayOf(0, 450, 350, 450, 2_500), 0))
        true
    }.getOrDefault(false)

    fun describe(context: Context): String = runCatching {
        val device = vibrator(context)
        val ids = if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(VibratorManager::class.java)?.vibratorIds?.joinToString().orEmpty()
        else "旧接口"
        "震动设备：服务存在=${device != null}，hasVibrator=${device?.hasVibrator()}，设备列表=[$ids]"
    }.getOrElse { "读取震动设备失败：${it.javaClass.simpleName}" }

    fun testWithReport(context: Context): String = runCatching {
        val device = vibrator(context) ?: return "震动请求未提交：安卓环境没有提供震动服务。"
        if (!device.hasVibrator()) return "震动请求未提交：安卓环境 hasVibrator=false。"
        request(device, VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        "已提交 0.5 秒来电震动请求，接口未抛异常；是否实际震动以手机反馈为准。"
    }.getOrElse { "震动请求异常：${it.javaClass.simpleName}（${it.message?.take(100).orEmpty()}）" }

    fun stop(context: Context) {
        runCatching { vibrator(context)?.cancel() }
    }
}
