package com.mobileclaude.app.voice

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Keep a call audible until it is answered, declined, or expires. The notification channel alone
 * does not reliably ring through every Android compatibility container. */
internal object YanjiCallRingtone {
    private var playing: Ringtone? = null

    private fun permitted(context: Context): Boolean {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val channel = notifications.getNotificationChannel(YanjiVoiceService.CALL_CHANNEL)
        val audio = context.getSystemService(AudioManager::class.java)
        return notifications.areNotificationsEnabled() && channel != null &&
            channel.importance != NotificationManager.IMPORTANCE_NONE && channel.sound != null &&
            notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE &&
            audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    private fun makeTone(context: Context): Ringtone? {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val uri = notifications.getNotificationChannel(YanjiVoiceService.CALL_CHANNEL).sound
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        return RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
    }

    @Synchronized
    fun start(context: Context): Boolean {
        stop()
        if (!permitted(context)) return false
        return runCatching {
            val tone = makeTone(context) ?: return false
            if (Build.VERSION.SDK_INT >= 28) tone.isLooping = true
            tone.play()
            playing = tone
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun keepPlaying() {
        runCatching { if (playing != null && playing?.isPlaying == false) playing?.play() }
    }

    fun testOnce(context: Context): Boolean {
        if (!permitted(context)) return false
        return runCatching {
            val tone = makeTone(context) ?: return false
            tone.play()
            Handler(Looper.getMainLooper()).postDelayed({ tone.stop() }, 3_000)
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun stop() {
        runCatching { playing?.stop() }
        playing = null
    }
}
