package com.mobileclaude.app.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class YanjiVoiceStartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching { YanjiVoiceService.disable(context) }
        }
    }
}
