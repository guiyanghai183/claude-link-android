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
    data class Downloading(val update: AppUpdate) : UpdateState
    data class Ready(val update: AppUpdate, val apkPath: String) : UpdateState
    data class Error(val message: String) : UpdateState
}
