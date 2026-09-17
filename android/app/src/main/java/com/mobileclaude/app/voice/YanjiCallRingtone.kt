package com.mobileclaude.app.voice

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mobileclaude.app.R
import java.util.concurrent.atomic.AtomicLong

/** App-owned audio avoids depending on the host's default ringtone URI inside Android containers. */
internal object YanjiCallRingtone {
    private const val TAG = "YanjiCallRingtone"
    private val main = Handler(Looper.getMainLooper())
    private val callGeneration = AtomicLong()
    private val testGeneration = AtomicLong()
    // All sessions, player operations, and callbacks live on the main thread.
    private var callSession: Playback? = null
    private var testSession: Playback? = null

    private class Playback(val context: Context, val generation: Long, val testing: Boolean,
                           val mediaMode: Boolean, val update: ((String) -> Unit)?) {
        val player = MediaPlayer()
        var prepared = false
        var released = false
        var timeout: Runnable? = null
        var tick: Runnable? = null
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    private fun permitted(context: Context): Boolean = runCatching {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val channel = notifications.getNotificationChannel(YanjiVoiceService.CALL_CHANNEL)
        val audio = context.getSystemService(AudioManager::class.java)
        notifications.areNotificationsEnabled() && channel != null &&
            channel.importance != NotificationManager.IMPORTANCE_NONE && channel.sound != null &&
            notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL &&
            audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }.getOrDefault(false)

    /** True means request accepted, not that the physical phone has produced sound. */
    fun start(context: Context): Boolean {
        val app = context.applicationContext
        val generation = callGeneration.incrementAndGet()
        val allowed = permitted(app)
        stopTest()
        onMain {
            if (callGeneration.get() != generation) return@onMain
            callSession?.let(::release)
            if (allowed && permitted(app)) {
                // A settings click could have started a new test between the posted tasks.
                testGeneration.incrementAndGet()
                testSession?.let(::release)
                begin(app, generation, false, false, null)
            }
        }
        return allowed
    }

    /** The player loops itself; never recreate a failed player on every network poll. */
    fun keepPlaying() = onMain {
        callSession?.let { if (!permitted(it.context)) stop() }
    }

    fun stop() {
        val generation = callGeneration.incrementAndGet()
        onMain { if (callGeneration.get() == generation) callSession?.let(::release) }
    }

    /** Media usage is a foreground diagnostic only, never an incoming-call fallback. */
    fun test(context: Context, mediaMode: Boolean, onUpdate: (String) -> Unit) {
        val app = context.applicationContext
        val generation = testGeneration.incrementAndGet()
        onMain {
            if (testGeneration.get() != generation) return@onMain
            testSession?.let(::release)
            if (callSession != null) {
                onUpdate("当前有来电正在响铃，请在来电结束后测试。")
            } else if (!mediaMode && !permitted(app)) {
                onUpdate("铃声测试未启动：请检查通知权限、来电渠道声音、静音及勿扰设置。${volumes(app)}")
            } else begin(app, generation, true, mediaMode, onUpdate)
        }
    }

    fun stopTest() {
        val generation = testGeneration.incrementAndGet()
        onMain { if (testGeneration.get() == generation) testSession?.let(::release) }
    }

    private fun active(session: Playback): Boolean = !session.released &&
        if (session.testing) testSession === session && testGeneration.get() == session.generation
        else callSession === session && callGeneration.get() == session.generation

    private fun begin(context: Context, generation: Long, testing: Boolean, mediaMode: Boolean,
                      update: ((String) -> Unit)?) {
        val session = try { Playback(context, generation, testing, mediaMode, update) }
        catch (error: Exception) {
            val message = "无法创建播放器：${error.javaClass.simpleName}。${volumes(context)}"
            Log.w(TAG, message)
            update?.invoke(message)
            return
        }
        if (testing) testSession = session else callSession = session
        try {
            session.player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(if (mediaMode) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            session.player.setOnPreparedListener {
                if (!active(session)) { release(session); return@setOnPreparedListener }
                session.prepared = true
                if (!testing && !permitted(context)) { release(session); return@setOnPreparedListener }
                try {
                    it.isLooping = true
                    it.start()
                    report(session, "prepared；start 已调用")
                    if (testing) scheduleTick(session)
                } catch (error: Exception) { fail(session, "start 错误：${error.javaClass.simpleName}") }
            }
            session.player.setOnErrorListener { _, what, extra ->
                if (active(session)) fail(session, "播放器 error=$what/$extra") else release(session)
                true
            }
            context.resources.openRawResourceFd(R.raw.yanji_ringtone).use { resource ->
                session.player.setDataSource(resource.fileDescriptor, resource.startOffset, resource.length)
            }
            report(session, "正在准备内置音频")
            session.timeout = Runnable {
                if (active(session) && (testing || !session.prepared)) {
                    report(session, if (testing) "3 秒测试结束；即将释放播放器" else "准备超时；即将释放播放器")
                    release(session)
                }
            }.also { main.postDelayed(it, if (testing) 3_000L else 5_000L) }
            session.player.prepareAsync()
        } catch (error: Exception) { fail(session, "准备错误：${error.javaClass.simpleName}") }
    }

    private fun scheduleTick(session: Playback) {
        val tick = object : Runnable {
            override fun run() {
                if (!active(session)) return
                report(session, "测试进行中")
                main.postDelayed(this, 500)
            }
        }
        session.tick = tick
        main.postDelayed(tick, 500)
    }

    private fun fail(session: Playback, message: String) {
        if (active(session)) report(session, message)
        release(session)
    }

    private fun report(session: Playback, event: String) {
        if (!active(session)) return
        val playing = if (session.prepared) runCatching { session.player.isPlaying.toString() }.getOrDefault("未知") else "尚未准备"
        val position = if (session.prepared) runCatching { "${session.player.currentPosition} ms" }.getOrDefault("未知") else "尚未准备"
        val route = if (Build.VERSION.SDK_INT >= 28 && session.prepared) runCatching {
            session.player.routedDevice?.let { "${it.productName}（type=${it.type}）" } ?: "未报告"
        }.getOrDefault("读取失败") else "未报告"
        val text = "内置双音 / ${if (session.mediaMode) "媒体用途" else "来电铃声用途"}：$event\n" +
            "prepared=${session.prepared}；isPlaying=$playing；position=$position；输出=$route\n" +
            volumes(session.context) + "\n以上是播放器报告，请以手机实际声音为准。"
        Log.i(TAG, text)
        session.update?.invoke(text)
    }

    private fun volumes(context: Context): String = runCatching {
        val audio = context.getSystemService(AudioManager::class.java)
        fun volume(stream: Int) = "${audio.getStreamVolume(stream)}/${audio.getStreamMaxVolume(stream)}"
        "音量：铃声 ${volume(AudioManager.STREAM_RING)}；媒体 ${volume(AudioManager.STREAM_MUSIC)}；通知 ${volume(AudioManager.STREAM_NOTIFICATION)}。"
    }.getOrDefault("音量信息不可用。")

    private fun release(session: Playback) {
        if (session.released) return
        session.released = true
        session.timeout?.let(main::removeCallbacks)
        session.tick?.let(main::removeCallbacks)
        if (callSession === session) callSession = null
        if (testSession === session) testSession = null
        runCatching { session.player.setOnPreparedListener(null) }
        runCatching { session.player.setOnErrorListener(null) }
        // Release also handles prepareAsync in flight; stale callbacks check the generation.
        runCatching { session.player.release() }
    }
}
