package com.mobileclaude.app.voice

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Request call vibration directly when a notification is shown. Some Android containers display
 * the call notification without forwarding its channel vibration to the physical phone. */
internal object YanjiCallVibration {
    private fun vibrator(context: Context): Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun channelAllowsVibration(context: Context): Boolean {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val channel = notifications.getNotificationChannel(YanjiVoiceService.CALL_CHANNEL)
        return notifications.areNotificationsEnabled() && channel != null &&
            channel.importance != NotificationManager.IMPORTANCE_NONE && channel.shouldVibrate() &&
            notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE
    }

    private fun vibrate(context: Context, effect: VibrationEffect): Boolean = runCatching {
        val device = vibrator(context)
        if (!device.hasVibrator()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            device.vibrate(effect, VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_RINGTONE).build())
        } else {
            @Suppress("DEPRECATION")
            device.vibrate(effect, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
        }
        true
    }.getOrDefault(false)

    fun start(context: Context): Boolean {
        if (!channelAllowsVibration(context)) return false
        return vibrate(context, VibrationEffect.createWaveform(longArrayOf(0, 450, 350, 450, 2_500), 0))
    }

    fun testOnce(context: Context): Boolean = vibrate(context,
        VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

    fun stop(context: Context) {
        runCatching { vibrator(context).cancel() }
    }
}
