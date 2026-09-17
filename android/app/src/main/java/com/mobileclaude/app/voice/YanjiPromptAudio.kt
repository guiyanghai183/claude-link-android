package com.mobileclaude.app.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

object YanjiPromptAudio {
    fun wav(pcm: ByteArray): ByteArray {
        require(pcm.size in 2..8 * 1024 * 1024 && pcm.size % 2 == 0) { "问题语音数据无效" }
        return ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(24_000)
            putInt(48_000)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
            put(pcm)
        }.array()
    }
}
