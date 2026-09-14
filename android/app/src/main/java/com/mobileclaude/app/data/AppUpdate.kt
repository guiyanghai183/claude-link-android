package com.mobileclaude.app.data

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(
        val update: AppUpdate,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long? = null,
    ) : UpdateState {
        val progress: Float?
            get() = totalBytes
                ?.takeIf { it > 0L }
                ?.let { total ->
                    (downloadedBytes.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                }

        val percent: Int?
            get() = progress?.times(100f)?.toInt()?.coerceIn(0, 100)
    }
    data class Ready(val update: AppUpdate, val apkPath: String) : UpdateState
    data class Error(val message: String) : UpdateState
}
