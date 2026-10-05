package com.mobileclaude.app.ui

import android.os.Bundle
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mobileclaude.app.web.YanjiWebPolicy
import com.mobileclaude.app.web.YanjiWebSession

@Composable
internal fun rememberYanjiWebSession(): YanjiWebSession {
    val context = LocalContext.current
    val history = rememberSaveable { Bundle() }
    val session = remember(context) { YanjiWebSession(context, history) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            session.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        session.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            session.release()
        }
    }
    return session
}

@Composable
internal fun YanjiWebScreen(
    session: YanjiWebSession,
    fullScreen: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    onExit: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var menuVisible by remember { mutableStateOf(false) }
    var readingVisible by remember { mutableStateOf(false) }
    var addressVisible by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(session.baseUrl) }
    var addressError by remember { mutableStateOf<String?>(null) }
    val fileChooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        session.completeFileChooser(WebChromeClient.FileChooserParams.parseResult(it.resultCode, it.data))
    }
    DisposableEffect(session, fileChooser) {
        session.launchFileChooser = { fileChooser.launch(it) }
        session.setVisible(true)
        onDispose {
            session.setVisible(false)
            session.launchFileChooser = null
            session.completeFileChooser(null)
        }
    }
    val message = session.message
    LaunchedEffect(message) {
        if (message != null) {
            session.clearMessage()
            onMessage(message)
        }
    }
    fun back() {
        when {
            fullScreen -> onFullScreenChange(false)
            session.canGoBack -> session.goBack()
            else -> onExit()
        }
    }
    BackHandler { back() }

    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .navigationBarsPadding()
            .imePadding()
    ) {
        if (!fullScreen) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "研记返回")
                    }
                    Text(
                        "研记", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { readingVisible = true }) { Text("字号") }
                    IconButton(onClick = session::reload) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新研记")
                    }
                    Box {
                        IconButton(onClick = { menuVisible = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "研记菜单")
                        }
                        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                            DropdownMenuItem(
                                text = { Text("全屏阅读") },
                                onClick = { menuVisible = false; onFullScreenChange(true) },
                            )
                            DropdownMenuItem(
                                text = { Text("研记主页") },
                                onClick = { menuVisible = false; session.home() },
                            )
                            DropdownMenuItem(
                                text = { Text("在浏览器中打开") },
                                onClick = { menuVisible = false; session.openInBrowser() },
                            )
                            DropdownMenuItem(
                                text = { Text("修改研记网址") },
                                onClick = {
                                    menuVisible = false
                                    address = session.baseUrl
                                    addressError = null
                                    addressVisible = true
                                },
                            )
                        }
                    }
                }
            }
        }
        if (session.progress in 0..99) {
            LinearProgressIndicator(
                progress = { session.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { session.view() },
                modifier = Modifier.fillMaxSize(),
                onReset = null,
                onRelease = { session.saveHistory() },
            )
            if (session.error != null) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("暂时无法打开研记", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(session.error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = session::reload) { Text("重试") }
                        TextButton(onClick = {
                            address = session.baseUrl
                            addressError = null
                            addressVisible = true
                        }) { Text("检查网址") }
                    }
                }
            }
            if (fullScreen) {
                FilledTonalButton(
                    onClick = { onFullScreenChange(false) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) { Text("退出全屏") }
            }
        }
    }

    if (readingVisible) {
        AlertDialog(
            onDismissRequest = { readingVisible = false },
            title = { Text("阅读字号") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "字号调整会立即生效，并记住你的选择。也可以用双指缩放查看图表。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    YanjiWebPolicy.textZoomOptions.forEach { value ->
                        TextButton(
                            onClick = { session.changeTextZoom(value) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when (value) {
                                    100 -> "标准 · 100%"
                                    110 -> "舒适 · 110%"
                                    125 -> "较大 · 125%"
                                    140 -> "大字 · 140%"
                                    else -> "特大 · 160%"
                                },
                                modifier = Modifier.weight(1f),
                                fontWeight = if (session.textZoom == value) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (session.textZoom == value) Text("✓")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { readingVisible = false }) { Text("完成") } },
        )
    }
    if (addressVisible) {
        AlertDialog(
            onDismissRequest = { addressVisible = false },
            title = { Text("研记网址") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("默认打开你现有的研记网站。修改网址将打开新页面。")
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it; addressError = null },
                        label = { Text("HTTPS 网址") },
                        isError = addressError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (addressError != null) {
                        Text(addressError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { session.changeUrl(address) }
                        .onSuccess { addressVisible = false }
                        .onFailure { addressError = it.message ?: "请检查网址" }
                }) { Text("保存并打开") }
            },
            dismissButton = { TextButton(onClick = { addressVisible = false }) { Text("取消") } },
        )
    }
}
