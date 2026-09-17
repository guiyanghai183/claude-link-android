package com.mobileclaude.app.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.mobileclaude.app.MainActivity
import com.mobileclaude.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class YanjiVoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager by lazy { getSystemService(NotificationManager::class.java) }
    private var polling: Job? = null
    private var notifiedId: String? = null
    private var lastError = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(NotificationChannel(STATUS_CHANNEL, "研记来电监听", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CALL_CHANNEL, "研记来电", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
        val notification = statusNotification("等待研记语音确认")
        if (Build.VERSION.SDK_INT >= 29) startForeground(STATUS_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        else startForeground(STATUS_NOTIFICATION, notification)
        polling = scope.launch { poll() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                YanjiVoiceConfig(this).clear()
                manager.cancel(CALL_NOTIFICATION)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DECLINE -> {
                val id = intent.getStringExtra(EXTRA_CALL_ID)
                if (id != null) scope.launch {
                    runCatching { client()?.decline(id) }
                    if (notifiedId == id) {
                        notifiedId = null
                        manager.cancel(CALL_NOTIFICATION)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun client(): YanjiVoiceClient? {
        val config = YanjiVoiceConfig(this)
        if (!config.enabled || config.url.isBlank() || config.token().isBlank()) return null
        return YanjiVoiceClient(config.url, config.token())
    }

    private suspend fun poll() {
        while (scope.isActive) {
            val api = client()
            if (api == null) {
                manager.cancel(CALL_NOTIFICATION)
                stopSelf()
                break
            }
            try {
                val call = api.pending()
                if (call != null && call.id != notifiedId) {
                    notifiedId = call.id
                    runCatching { manager.notify(CALL_NOTIFICATION, incomingNotification(call)) }
                } else if (call == null && notifiedId != null) {
                    notifiedId = null
                    manager.cancel(CALL_NOTIFICATION)
                }
                if (lastError.isNotEmpty()) {
                    lastError = ""
                    runCatching { manager.notify(STATUS_NOTIFICATION, statusNotification("等待研记语音确认")) }
                }
            } catch (error: Exception) {
                val message = error.message?.take(60) ?: "连接中断"
                if (message != lastError) {
                    lastError = message
                    runCatching { manager.notify(STATUS_NOTIFICATION, statusNotification("连接失败，稍后重试")) }
                }
            }
            delay(5_000)
        }
    }

    private fun statusNotification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Claude Link · 研记来电")
            .setContentText(message)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun incomingNotification(call: YanjiCall): Notification {
        val open = PendingIntent.getActivity(this, 20, Intent(this, YanjiCallActivity::class.java)
            .putExtra(EXTRA_CALL_ID, call.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val decline = PendingIntent.getService(this, 21, Intent(this, YanjiVoiceService::class.java)
            .setAction(ACTION_DECLINE).putExtra(EXTRA_CALL_ID, call.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("研记语音确认")
            .setContentText(call.question.take(80))
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setStyle(Notification.CallStyle.forIncomingCall(
                android.app.Person.Builder().setName("ChatGPT · 研记").build(), decline, open))
        } else {
            builder.addAction(Notification.Action.Builder(null, "拒接", decline).build())
            builder.addAction(Notification.Action.Builder(null, "接听", open).build())
        }
        return builder.build()
    }

    override fun onDestroy() {
        polling?.cancel()
        scope.cancel()
        manager.cancel(CALL_NOTIFICATION)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CALL_ID = "yanji_call_id"
        private const val ACTION_STOP = "com.mobileclaude.app.voice.STOP"
        private const val ACTION_DECLINE = "com.mobileclaude.app.voice.DECLINE"
        private const val STATUS_CHANNEL = "yanji_voice_status"
        private const val CALL_CHANNEL = "yanji_voice_incoming"
        private const val STATUS_NOTIFICATION = 1801
        private const val CALL_NOTIFICATION = 1802

        fun start(context: Context) {
            if (YanjiVoiceConfig(context).enabled) context.startForegroundService(Intent(context, YanjiVoiceService::class.java))
        }
        fun stop(context: Context) {
            context.startService(Intent(context, YanjiVoiceService::class.java).setAction(ACTION_STOP))
        }
    }
}
