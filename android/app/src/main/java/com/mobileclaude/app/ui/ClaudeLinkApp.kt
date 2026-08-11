package com.mobileclaude.app.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mobileclaude.app.AppViewModel
import com.mobileclaude.app.BuildConfig
import com.mobileclaude.app.R
import com.mobileclaude.app.data.Artifact
import com.mobileclaude.app.data.ChatDetail
import com.mobileclaude.app.data.ChatMessage
import com.mobileclaude.app.data.ChatSummary
import com.mobileclaude.app.data.ConnectionStatus
import com.mobileclaude.app.data.DeepSeekBalanceInfo
import com.mobileclaude.app.data.GpuInfo
import com.mobileclaude.app.data.GpuQueueJob
import com.mobileclaude.app.data.GpuQueueSnapshot
import com.mobileclaude.app.data.GpuSnapshot
import com.mobileclaude.app.data.MainTab
import com.mobileclaude.app.data.RemoteFileEntry
import com.mobileclaude.app.data.ServerProfile
import com.mobileclaude.app.data.TerminalStatus
import com.mobileclaude.app.data.UpdateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

@Composable
fun ClaudeLinkApp(viewModel: AppViewModel) {
    val snackbars = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val error = viewModel.errorMessage
    LaunchedEffect(error) {
        if (error != null) {
            snackbars.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbars) },
        bottomBar = {
            if (viewModel.activeProfile != null && !keyboardVisible) {
                BottomTabs(viewModel.selectedTab, viewModel::selectTab)
            }
        },
    ) { contentPadding ->
        val bottomPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottomPadding)
                .consumeWindowInsets(bottomPadding)
        ) {
            if (viewModel.activeProfile == null) {
                ServerLanding(viewModel)
            } else {
                AnimatedContent(viewModel.selectedTab, label = "main-tabs") { tab ->
                    when (tab) {
                        MainTab.CHATS -> if (viewModel.activeChat == null) {
                            ChatHistoryScreen(viewModel)
                        } else {
                            ChatScreen(viewModel, viewModel.activeChat!!)
                        }
                        MainTab.BROWSER -> BrowserScreen(viewModel)
                        MainTab.FILES -> RemoteFilesScreen(viewModel)
                        MainTab.GPU -> GpuScreen(viewModel)
                        MainTab.SERVERS -> ServerLanding(viewModel)
                    }
                }
            }
        }
    }

    if (viewModel.addServerVisible) {
        AddServerSheet(viewModel)
    }
    if (viewModel.folderPickerVisible && viewModel.folderListing != null) {
        FolderPicker(viewModel)
    }
    if (viewModel.newChatFolder != null) {
        NewChatModeSheet(viewModel)
    }
    if (viewModel.ocrPreviewDraft != null) {
        OcrPreviewSheet(viewModel)
    }
    if (viewModel.remoteFilePreview != null) {
        RemoteFilePreviewSheet(viewModel)
    }
    UpdateDialog(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrPreviewSheet(viewModel: AppViewModel) {
    val draft = viewModel.ocrPreviewDraft ?: return
    ModalBottomSheet(
        onDismissRequest = viewModel::cancelOcrPreview,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
        ) {
            Text("OCR 内容预览", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                draft.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                draft.url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${draft.content.length} 字 · 可直接修改",
                    color = AppleBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (draft.content.length >= 300_000) {
                    Spacer(Modifier.weight(1f))
                    Text("已到 30 万字上限", color = WarmOrange, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            ) {
                BasicTextField(
                    value = draft.content,
                    onValueChange = viewModel::updateOcrPreview,
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = viewModel::cancelOcrPreview,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = viewModel::confirmOcrPreview,
                    enabled = draft.content.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("确认附加")
                }
            }
        }
    }
}

@Composable
private fun BottomTabs(selected: MainTab, onSelect: (MainTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 12.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .height(58.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabButton("◉", "对话", selected == MainTab.CHATS, Modifier.weight(1f)) { onSelect(MainTab.CHATS) }
            TabButton("◎", "浏览器", selected == MainTab.BROWSER, Modifier.weight(1f)) { onSelect(MainTab.BROWSER) }
            TabButton("▤", "文件", selected == MainTab.FILES, Modifier.weight(1f)) { onSelect(MainTab.FILES) }
            TabButton("▥", "算力", selected == MainTab.GPU, Modifier.weight(1f)) { onSelect(MainTab.GPU) }
            TabButton("▣", "服务器", selected == MainTab.SERVERS, Modifier.weight(1f)) { onSelect(MainTab.SERVERS) }
        }
    }
}

@Composable
private fun TabButton(
    symbol: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(symbol, fontSize = 18.sp, color = if (selected) AppleBlue else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AppleBlue else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        action?.invoke()
    }
}

@Composable
private fun ServerLanding(viewModel: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Claude Link",
            subtitle = "你的局域网实验工作台",
            action = {
                IconButton(onClick = { viewModel.addServerVisible = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加服务器", tint = AppleBlue)
                }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (viewModel.profiles.isEmpty()) {
                item { EmptyServersCard { viewModel.addServerVisible = true } }
            }
            items(viewModel.profiles, key = { it.id }) { profile ->
                ServerCard(
                    profile = profile,
                    active = viewModel.activeProfile?.id == profile.id,
                    status = viewModel.connectionStatus,
                    onConnect = { viewModel.connect(profile) },
                    onDisconnect = viewModel::disconnect,
                    onDelete = { viewModel.deleteProfile(profile) },
                )
            }
            if (viewModel.activeProfile != null) {
                item { DeepSeekBalanceCard(viewModel) }
            }
            item { UpdateSettingsCard(viewModel) }
            item {
                PrivacyCard()
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun UpdateSettingsCard(viewModel: AppViewModel) {
    val state = viewModel.updateState
    val subtitle = when (state) {
        UpdateState.Checking -> "正在检查 GitHub Release…"
        UpdateState.UpToDate -> "已经是最新版本"
        is UpdateState.Available -> "发现新版本 ${state.update.versionName}"
        is UpdateState.Downloading -> "正在下载并校验 ${state.update.versionName}…"
        is UpdateState.Ready -> "版本 ${state.update.versionName} 已下载"
        is UpdateState.Error -> state.message
        UpdateState.Idle -> "自动检查 GitHub Release"
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = AppleBlue.copy(alpha = 0.11f)) {
                Text("↻", color = AppleBlue, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("版本 ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            when (state) {
                UpdateState.Checking, is UpdateState.Downloading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                is UpdateState.Available -> TextButton(onClick = viewModel::downloadUpdate) { Text("更新") }
                is UpdateState.Ready -> TextButton(onClick = viewModel::installUpdate) { Text("安装") }
                else -> TextButton(onClick = { viewModel.checkForUpdates() }) { Text("检查") }
            }
        }
    }
}

@Composable
private fun UpdateDialog(viewModel: AppViewModel) {
    when (val state = viewModel.updateState) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = viewModel::dismissUpdate,
            title = { Text("发现新版本 ${state.update.versionName}") },
            text = {
                Column {
                    Text("更新包将从 GitHub Release 下载，并在安装前校验 SHA-256。")
                    if (state.update.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(state.update.releaseNotes, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 9, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            confirmButton = { Button(onClick = viewModel::downloadUpdate) { Text("下载并安装") } },
            dismissButton = { TextButton(onClick = viewModel::dismissUpdate) { Text("稍后") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在准备更新") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("正在下载并校验 ${state.update.versionName}，请稍候…")
                }
            },
            confirmButton = {},
        )
        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = viewModel::dismissUpdate,
            title = { Text("更新包已就绪") },
            text = { Text("如果系统要求，请先允许 Claude Link 安装未知应用，然后返回继续安装。") },
            confirmButton = { Button(onClick = viewModel::installUpdate) { Text("继续安装") } },
            dismissButton = { TextButton(onClick = viewModel::dismissUpdate) { Text("稍后") } },
        )
        else -> Unit
    }
}

@Composable
private fun EmptyServersCard(onAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = AppleBlue.copy(alpha = 0.12f)) {
                Text("⌁", fontSize = 34.sp, color = AppleBlue, modifier = Modifier.padding(18.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("连接你的服务器", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                "首次使用密码验证，随后通过这台手机的独立密钥登录。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Button(onClick = onAdd, shape = RoundedCornerShape(14.dp)) { Text("添加服务器") }
        }
    }
}

@Composable
private fun ServerCard(
    profile: ServerProfile,
    active: Boolean,
    status: ConnectionStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (active) Mint.copy(alpha = 0.17f) else AppleBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Text(if (active) "✓" else "⌁", color = if (active) Mint else AppleBlue, fontSize = 22.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(
                        "${profile.username}@${profile.host}:${profile.port}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                if (!active) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (profile.fingerprint.isNotBlank()) {
                Text(
                    profile.fingerprint,
                    modifier = Modifier.padding(top = 14.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            if (active) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Mint.copy(alpha = 0.14f)) {
                        Text("已加密连接", color = Mint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDisconnect) { Text("断开") }
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = status !is ConnectionStatus.Connecting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (status is ConnectionStatus.Connecting) status.message else "连接")
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除服务器？") },
            text = { Text("会同时删除手机中为该服务器保存的加密私钥。服务器项目不会受影响。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PrivacyCard() {
    Surface(shape = RoundedCornerShape(20.dp), color = AppleBlue.copy(alpha = 0.08f)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            Text("◈", color = AppleBlue, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("连接保持私密", fontWeight = FontWeight.SemiBold)
                Text(
                    "Claude 服务仅监听服务器本机地址，手机通过 SSH 隧道访问。密码不会保存在应用中。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun DeepSeekBalanceCard(viewModel: AppViewModel) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    val balance = viewModel.deepSeekBalance?.balanceInfos?.firstOrNull()

    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color(0xFF6E56CF).copy(alpha = 0.13f)) {
                    Text("◌", color = Color(0xFF6E56CF), fontSize = 22.sp, modifier = Modifier.padding(9.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("DeepSeek API 余额", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("密钥仅加密保存在手机；每次查询经 SSH 临时转发", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (viewModel.deepSeekConfigured) {
                    IconButton(onClick = viewModel::refreshDeepSeekBalance, enabled = !viewModel.deepSeekBusy) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新 DeepSeek 余额", tint = AppleBlue)
                    }
                }
            }
            if (viewModel.deepSeekConfigured && balance != null) {
                Spacer(Modifier.height(16.dp))
                BalanceRings(balance, viewModel.deepSeekBalance?.isAvailable == true)
                Spacer(Modifier.height(8.dp))
                Text(
                    "DeepSeek 公开接口目前提供实时余额，未提供按日或按模型的消耗明细；圆环显示余额构成。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { editing = true }) { Text("更换密钥") }
                TextButton(onClick = viewModel::removeDeepSeekApiKey) { Text("移除密钥", color = MaterialTheme.colorScheme.error) }
            } else if (!editing) {
                Spacer(Modifier.height(14.dp))
                Text(
                    if (viewModel.deepSeekConfigured) "点击刷新以查看实时余额。" else "连接你的 DeepSeek 账户后，可在这里查看实时余额。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (viewModel.deepSeekConfigured) viewModel.refreshDeepSeekBalance() else editing = true
                    },
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text(if (viewModel.deepSeekConfigured) "刷新余额" else "设置 API Key")
                }
            }
            if (editing) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("DeepSeek API Key") },
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Text(if (apiKeyVisible) "隐藏" else "显示", fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { editing = false; apiKey = "" }) { Text("取消") }
                    Button(
                        onClick = {
                            viewModel.saveDeepSeekApiKey(apiKey)
                            apiKey = ""
                            editing = false
                        },
                        enabled = apiKey.isNotBlank(),
                        shape = RoundedCornerShape(13.dp),
                    ) { Text("保存并查询") }
                }
            }
            if (viewModel.deepSeekBusy) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在读取实时余额…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BalanceRings(balance: DeepSeekBalanceInfo, available: Boolean) {
    val total = balance.totalBalance.toDecimal()
    val granted = balance.grantedBalance.toDecimal()
    val toppedUp = balance.toppedUpBalance.toDecimal()
    val grantedProgress = granted.ratioOf(total)
    val toppedUpProgress = toppedUp.ratioOf(total)
    val outerColor = if (available) AppleBlue else MaterialTheme.colorScheme.error
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val outerStroke = 12.dp.toPx()
                val innerStroke = 10.dp.toPx()
                drawArc(outerColor.copy(alpha = 0.14f), -90f, 360f, false, style = Stroke(outerStroke))
                drawArc(outerColor, -90f, 360f * toppedUpProgress, false, style = Stroke(outerStroke))
                val inset = 18.dp.toPx()
                drawArc(Color(0xFF6E56CF).copy(alpha = 0.14f), -90f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(innerStroke))
                drawArc(Color(0xFF6E56CF), -90f, 360f * grantedProgress, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(innerStroke))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(balance.totalBalance, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(balance.currency.ifBlank { "余额" }, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            BalanceLegend(outerColor, "充值余额", balance.toppedUpBalance)
            BalanceLegend(Color(0xFF6E56CF), "赠送余额", balance.grantedBalance)
            Text(if (available) "当前可调用 API" else "当前余额不足", fontSize = 12.sp, color = if (available) Mint else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BalanceLegend(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(7.dp))
        Text("$label $value", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun String.toDecimal(): BigDecimal = runCatching { BigDecimal(this) }.getOrDefault(BigDecimal.ZERO)

private fun BigDecimal.ratioOf(total: BigDecimal): Float = if (total > BigDecimal.ZERO) {
    divide(total, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
} else {
    0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddServerSheet(viewModel: AppViewModel) {
    var name by rememberSaveable { mutableStateOf("实验服务器") }
    var host by rememberSaveable { mutableStateOf("172.18.40.74") }
    var port by rememberSaveable { mutableStateOf("22") }
    var username by rememberSaveable { mutableStateOf("gyhai") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { if (!viewModel.busy && viewModel.profiles.isNotEmpty()) viewModel.addServerVisible = false },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("添加服务器", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("首次连接成功后会自动切换为免密码密钥登录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(host, { host = it }, label = { Text("IP 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(username, { username = it }, label = { Text("用户名") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(
                    port,
                    { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("端口") },
                    modifier = Modifier.width(110.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("首次登录密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        PasswordEye(visible = passwordVisible)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            val connecting = viewModel.connectionStatus as? ConnectionStatus.Connecting
            AnimatedVisibility(connecting != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(connecting?.message.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = { viewModel.enroll(name, host, port.toIntOrNull() ?: 22, username, password) },
                enabled = !viewModel.busy && host.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("连接并启用免密登录") }
        }
    }
}

@Composable
private fun PasswordEye(visible: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    Canvas(Modifier.size(22.dp)) {
        drawOval(color = color, style = Stroke(width = 1.8.dp.toPx()))
        drawCircle(color = color, radius = 3.dp.toPx(), center = center)
        if (!visible) {
            drawLine(
                color = surfaceColor,
                start = androidx.compose.ui.geometry.Offset(2.dp.toPx(), 20.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(20.dp.toPx(), 2.dp.toPx()),
                strokeWidth = 4.dp.toPx(),
            )
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(2.dp.toPx(), 20.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(20.dp.toPx(), 2.dp.toPx()),
                strokeWidth = 1.8.dp.toPx(),
            )
        }
    }
}

@Composable
private fun ChatHistoryScreen(viewModel: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "对话",
            subtitle = (viewModel.connectionStatus as? ConnectionStatus.Connected)?.health?.hostname,
            action = {
                IconButton(onClick = { viewModel.showFolderPicker() }) {
                    Icon(Icons.Default.Add, contentDescription = "新对话", tint = AppleBlue)
                }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { RetentionBanner() }
            if (viewModel.chats.isEmpty()) {
                item { EmptyChatsCard { viewModel.showFolderPicker() } }
            }
            items(viewModel.chats, key = { it.id }) { chat ->
                ChatHistoryRow(chat, onOpen = { viewModel.openChat(chat.id) }, onDelete = { viewModel.deleteChat(chat.id) })
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun RetentionBanner() {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("7日", color = AppleBlue, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text("对话自动清理 · 标记永久保存的对话除外", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyChatsCard(onChooseFolder: () -> Unit) {
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✦", color = AppleBlue, fontSize = 34.sp)
            Text("从一个项目开始", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("先选择服务器目录，再创建 Claude 对话或远程终端对话。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
            FilledTonalButton(onClick = onChooseFolder, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Home, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择项目目录")
            }
        }
    }
}

@Composable
private fun ChatHistoryRow(chat: ChatSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val terminal = chat.mode == "terminal"
    val accent = if (terminal) Color(0xFF30A46C) else if (chat.pinned) WarmOrange else AppleBlue
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(
                    accent.copy(alpha = 0.12f)
                ),
                contentAlignment = Alignment.Center,
            ) { Text(if (terminal) ">_" else if (chat.pinned) "★" else "✦", color = accent, fontFamily = if (terminal) FontFamily.Monospace else null) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (terminal) {
                        Text("SSH 终端", color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(7.dp))
                    }
                    if (chat.status == "running") {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                }
                Text(chat.preview.ifBlank { chat.projectPath }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(chat.projectPath, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
            }
            IconButton(onClick = { confirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("删除这段对话？") },
            text = { Text("聊天历史会立即删除，项目中的实验文件不会被删除。") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ChatScreen(viewModel: AppViewModel, detail: ChatDetail) {
    val terminal = detail.chat.mode == "terminal"
    var headerExpanded by rememberSaveable(detail.chat.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (headerExpanded) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(WindowInsets.statusBars.asPaddingValues()).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = viewModel::closeChat) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                    Column(Modifier.weight(1f)) {
                        Text(detail.chat.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (terminal) "SSH 远程终端 · " else "") +
                                detail.chat.projectPath.substringAfterLast('/').ifBlank { detail.chat.projectPath },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = viewModel::togglePinned) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = if (detail.chat.pinned) "取消永久保存" else "永久保存",
                            tint = if (detail.chat.pinned) WarmOrange else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                ProjectStrip(detail.chat.projectPath, detail.chat.pinned) {
                    viewModel.showFolderPicker(detail.chat.projectPath)
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .height(38.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::closeChat, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", modifier = Modifier.size(20.dp))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = "显示对话标题和目录" }
                        .clickable { headerExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(34.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    )
                }
                IconButton(onClick = viewModel::togglePinned, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = if (detail.chat.pinned) "取消永久保存" else "永久保存",
                        tint = if (detail.chat.pinned) WarmOrange else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        MessageList(
            viewModel,
            detail,
            Modifier.weight(1f),
            onBackgroundTap = { headerExpanded = false },
            dismissHeaderOnTap = headerExpanded,
        )
        if (terminal) {
            TerminalComposer(viewModel, detail)
        } else {
            Composer(viewModel, chatId = detail.chat.id, running = detail.chat.status == "running")
        }
    }
}

@Composable
private fun ProjectStrip(path: String, pinned: Boolean, onFolder: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), modifier = Modifier.weight(1f).clickable(onClick = onFolder)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(17.dp), tint = AppleBlue)
                Spacer(Modifier.width(8.dp))
                Text(path, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (pinned) {
            Spacer(Modifier.width(8.dp))
            Text("永久保存", color = WarmOrange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MessageList(
    viewModel: AppViewModel,
    detail: ChatDetail,
    modifier: Modifier = Modifier,
    onBackgroundTap: () -> Unit = {},
    dismissHeaderOnTap: Boolean = false,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(
        detail.messages.size,
        detail.messages.lastOrNull()?.content,
        viewModel.terminalLiveOutput,
    ) {
        if (detail.messages.isNotEmpty()) listState.animateScrollToItem(detail.messages.lastIndex)
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (dismissHeaderOnTap) {
                    Modifier.pointerInput(detail.chat.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Final,
                            )
                            val handledByChild = down.isConsumed
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                            if (!handledByChild && up != null && !up.isConsumed) {
                                onBackgroundTap()
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (detail.messages.isEmpty()) {
            item {
                Column(Modifier.fillParentMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (detail.chat.mode == "terminal") ">_" else "✦", color = if (detail.chat.mode == "terminal") Color(0xFF30A46C) else AppleBlue, fontSize = 38.sp, fontFamily = if (detail.chat.mode == "terminal") FontFamily.Monospace else null)
                    Text(if (detail.chat.mode == "terminal") "远程终端已准备" else "准备好开始实验", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(
                        if (detail.chat.mode == "terminal") "命令将在服务器的 ${detail.chat.projectPath} 中执行。" else "描述目标，Claude 会在已挂载目录中工作。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(detail.messages, key = { it.id }) { message ->
            MessageBubble(viewModel, message, detail)
        }
    }
}

@Composable
private fun MessageBubble(viewModel: AppViewModel, message: ChatMessage, detail: ChatDetail) {
    val user = message.role == "user"
    val videoLinks = if (!user && message.status != "streaming") {
        message.content.extractVideoLinks().take(MAX_VIDEOS_PER_MESSAGE)
    } else {
        emptyList()
    }
    val displayContent = if (videoLinks.isNotEmpty()) {
        message.content.withoutVideoControlLines()
    } else {
        message.content
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        when (message.kind) {
            "video" -> {
                val url = message.metadata["url"].orEmpty()
                if (url.isNotBlank()) {
                    VideoPlayerCard(
                        url = url,
                        title = message.metadata["title"].orEmpty().ifBlank { message.content },
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    ) {
                        Text(
                            "视频链接缺失，无法播放",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
            "artifact" -> {
                val artifact = detail.artifacts.firstOrNull { it.id == message.metadata["id"] || it.name == message.content }
                if (artifact != null) ArtifactCard(viewModel, artifact)
            }
            "terminal_input" -> Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppleBlue,
                modifier = Modifier.fillMaxWidth(0.92f),
            ) {
                SelectionContainer {
                    Text(
                        "$ ${message.content}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                }
            }
            "terminal_output" -> {
                val content = if (viewModel.terminalLiveOutputMessageId == message.id) {
                    viewModel.terminalLiveOutput
                } else {
                    message.content
                }
                TerminalOutputCard(content, message.status == "streaming")
            }
            "tool" -> ToolCard(message)
            "approval" -> ApprovalCard(
                message = message,
                onAllow = { viewModel.resolveApproval(message.id, true) },
                onDeny = { viewModel.resolveApproval(message.id, false) },
            )
            "error" -> Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)) {
                Text(message.content, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(14.dp))
            }
            "status" -> Text(message.content, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (user) 20.dp else 6.dp,
                    bottomEnd = if (user) 6.dp else 20.dp,
                ),
                color = if (user) AppleBlue else MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(if (user) 0.84f else 0.94f),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (displayContent.isNotBlank()) {
                        Text(displayContent, color = if (user) Color.White else MaterialTheme.colorScheme.onSurface, lineHeight = 21.sp)
                    }
                    videoLinks.forEach { videoUrl ->
                        if (displayContent.isNotBlank()) Spacer(Modifier.height(10.dp))
                        VideoPlayerCard(videoUrl, "Claude Link 视频")
                    }
                    val webAttachmentChars = message.metadata["webAttachmentChars"]?.toIntOrNull() ?: 0
                    if (user && webAttachmentChars > 0) {
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.16f)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Text("✓ Claude 已收到网页 OCR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${message.metadata["webAttachmentTitle"].orEmpty()} · $webAttachmentChars 字",
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (message.status == "streaming") {
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = AppleBlue)
                            Spacer(Modifier.width(6.dp))
                            Text("Claude 正在输入", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalOutputCard(content: String, streaming: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF11151A),
        modifier = Modifier.fillMaxWidth(0.98f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "REMOTE",
                    color = Color(0xFF67D391),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (streaming) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                        color = Color(0xFF67D391),
                    )
                }
            }
            if (content.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                SelectionContainer {
                    Text(
                        content,
                        color = Color(0xFFE6EDF3),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCard(message: ChatMessage) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f), modifier = Modifier.fillMaxWidth(0.94f)) {
        Column(Modifier.padding(13.dp)) {
            Text("正在使用工具", fontSize = 11.sp, color = AppleBlue, fontWeight = FontWeight.SemiBold)
            Text(message.content.substringBefore('\n'), fontWeight = FontWeight.Medium)
            val detail = message.content.substringAfter('\n', "")
            if (detail.isNotBlank()) Text(detail, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ApprovalCard(message: ChatMessage, onAllow: () -> Unit, onDeny: () -> Unit) {
    val pending = message.status == "pending"
    val stateText = when (message.status) {
        "allowed" -> "已允许，本次操作正在继续"
        "denied" -> "已拒绝本次操作"
        "expired" -> "请求已失效"
        else -> "等待你的确认"
    }
    val stateColor = when (message.status) {
        "allowed" -> Color(0xFF28A745)
        "denied", "expired" -> MaterialTheme.colorScheme.error
        else -> WarmOrange
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(0.96f),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = WarmOrange.copy(alpha = 0.14f)) {
                    Text("!", color = WarmOrange, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Claude 请求执行操作", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(stateText, color = stateColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                message.metadata["description"].orEmpty().ifBlank { message.metadata["displayName"] ?: "终端操作" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(7.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)) {
                Text(
                    message.content,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            message.metadata["blockedPath"]?.takeIf { it.isNotBlank() }?.let { path ->
                Spacer(Modifier.height(7.dp))
                Text("影响路径：$path", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (pending) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onDeny, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                        Text("拒绝")
                    }
                    Button(onClick = onAllow, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                        Text("本次允许")
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactCard(viewModel: AppViewModel, artifact: Artifact) {
    val image = viewModel.artifactImages[artifact.id]
    LaunchedEffect(artifact.id) { if (artifact.mimeType.startsWith("image/")) viewModel.loadArtifact(artifact.id) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(0.94f)) {
        Column {
            if (image != null) {
                Image(
                    image.asImageBitmap(),
                    contentDescription = artifact.name,
                    modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else if (artifact.mimeType.startsWith("image/")) {
                Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
            if (artifact.mimeType.startsWith("video/")) {
                viewModel.artifactContentUrl(artifact.id)?.let { videoUrl ->
                    VideoPlayerCard(videoUrl, artifact.name)
                }
            }
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        artifact.mimeType.startsWith("image/") -> "▧"
                        artifact.mimeType.startsWith("video/") -> "▶"
                        else -> "▤"
                    },
                    color = AppleBlue,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(artifact.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artifact.path, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerCard(url: String, title: String) {
    val context = LocalContext.current
    var retryKey by remember(url) { mutableStateOf(0) }
    var playerError by remember(url, retryKey) { mutableStateOf<String?>(null) }
    val player = remember(url, retryKey) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = error.localizedMessage?.take(160) ?: "视频源暂时无法播放"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = Color.Black, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) {
        Column {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
            Text(title, color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            playerError?.let { error ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("无法播放：$error", color = Color(0xFFFF9F9F), fontSize = 11.sp)
                    Row {
                        TextButton(onClick = { retryKey += 1 }) { Text("重试") }
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                        ) { Text("外部打开") }
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Claude Link 视频", url))
                            },
                        ) { Text("复制链接") }
                    }
                }
            }
        }
    }
}

private fun String.extractVideoLinks(): List<String> {
    val marked = Regex("""(?im)^\s*(?:[-*]\s*)?`{0,3}claude-link-video\s*:\s*(https?://[^\s<>()\[\]\"`]+)""")
        .findAll(this)
        .map { it.groupValues[1] }
    val direct = Regex("""(?i)https?://[^\s<>()\[\]\"]+?\.(?:mp4|webm|mov|m4v|m3u8|mpd)(?:\?[^\s<>()\[\]\"]*)?""")
        .findAll(this)
        .map { it.value }
    return (marked + direct)
        .map { it.trimEnd('.', ',', ';', ':', ')', ']', '`', '。', '，', '；', '：') }
        .distinct()
        .toList()
}

private fun String.withoutVideoControlLines(): String = lineSequence()
    .filterNot {
        it.trimStart()
            .removePrefix("-")
            .removePrefix("*")
            .trimStart()
            .trimStart('`')
            .startsWith("claude-link-video:", ignoreCase = true)
    }
    .joinToString("\n")
    .trim()

private const val MAX_VIDEOS_PER_MESSAGE = 2
private const val MAX_TERMINAL_COMMAND_CHARS = 16_000

@Composable
private fun TerminalComposer(viewModel: AppViewModel, detail: ChatDetail) {
    var text by remember(detail.chat.id) { mutableStateOf("") }
    val history = detail.messages.filter { it.kind == "terminal_input" }.map { it.content }
    var historyIndex by remember(detail.chat.id) { mutableStateOf(history.size) }
    val status = viewModel.terminalStatus
    val connected = status is TerminalStatus.Connected
    val running = viewModel.terminalCommandRunning
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(history.size) {
        if (historyIndex > history.size) historyIndex = history.size
    }

    fun submit() {
        if (!connected || viewModel.terminalCommandSending) return
        val submitted = text
        viewModel.sendTerminalCommand(submitted) {
            if (text == submitted) text = ""
            historyIndex = history.size + 1
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 7.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 10.dp)
                .padding(top = 5.dp, bottom = if (keyboardVisible) 3.dp else 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusText = when (status) {
                    TerminalStatus.Connecting -> "连接中…"
                    TerminalStatus.Connected -> if (running) "程序运行中" else "SSH 已连接"
                    TerminalStatus.Disconnected -> "SSH 未连接"
                    is TerminalStatus.Error -> status.message
                }
                Text(
                    statusText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when (status) {
                        TerminalStatus.Connected -> Color(0xFF30A46C)
                        is TerminalStatus.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!connected && status !is TerminalStatus.Connecting) {
                    TextButton(
                        onClick = viewModel::reconnectTerminal,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text("重连", fontSize = 11.sp) }
                }
                TextButton(
                    onClick = { viewModel.sendTerminalControl(3) },
                    enabled = connected,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                ) { Text("^C", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                TextButton(
                    onClick = { viewModel.sendTerminalControl(4) },
                    enabled = connected,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                ) { Text("^D", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                TextButton(
                    onClick = {
                        if (history.isNotEmpty()) {
                            historyIndex = (historyIndex - 1).coerceIn(0, history.lastIndex)
                            text = history[historyIndex]
                        }
                    },
                    enabled = !running && history.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) { Text("↑", fontSize = 16.sp) }
                TextButton(
                    onClick = {
                        if (historyIndex < history.lastIndex) {
                            historyIndex += 1
                            text = history[historyIndex]
                        } else {
                            historyIndex = history.size
                            text = ""
                        }
                    },
                    enabled = !running && history.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) { Text("↓", fontSize = 16.sp) }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.replace("\r", "").replace("\n", "").take(MAX_TERMINAL_COMMAND_CHARS)
                        historyIndex = history.size
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    singleLine = true,
                    enabled = connected && !viewModel.terminalCommandSending,
                    placeholder = {
                        if (running) Text("标准输入", fontSize = 12.sp)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(7.dp))
                IconButton(
                    onClick = ::submit,
                    enabled = connected && !viewModel.terminalCommandSending && (text.isNotBlank() || running),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (connected && !viewModel.terminalCommandSending && (text.isNotBlank() || running)) AppleBlue
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                ) {
                    if (viewModel.terminalCommandSending) {
                        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "发送到远程终端", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatModeSheet(viewModel: AppViewModel) {
    val path = viewModel.newChatFolder ?: return
    ModalBottomSheet(
        onDismissRequest = viewModel::dismissNewChatModePicker,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("创建对话", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                path,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { viewModel.createChatForSelectedFolder("claude") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("✦  Claude 对话", fontWeight = FontWeight.Bold)
                    Text("让 Claude 在这个服务器目录中工作", fontSize = 11.sp, color = Color.White.copy(alpha = 0.82f))
                }
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = { viewModel.createChatForSelectedFolder("terminal") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(">_  远程终端对话", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("通过 SSH 直接进入服务器上的这个目录", fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Composer(viewModel: AppViewModel, chatId: String, running: Boolean) {
    var text by rememberSaveable(chatId) { mutableStateOf("") }
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 6.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 10.dp)
                .padding(top = 6.dp, bottom = if (keyboardVisible) 3.dp else 6.dp)
        ) {
            val attachment = viewModel.pendingWebAttachment
            AnimatedVisibility(attachment != null, enter = fadeIn(), exit = fadeOut()) {
                Surface(shape = RoundedCornerShape(13.dp), color = AppleBlue.copy(alpha = 0.10f), modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("◎", color = AppleBlue)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(attachment?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            Text(
                                "待发送 · ${attachment?.content?.length ?: 0} 字 OCR",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                            )
                        }
                        IconButton(onClick = viewModel::removeWebAttachment, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(17.dp)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        minLines = 1,
                        maxLines = 4,
                        decorationBox = { inner ->
                            if (text.isBlank()) Text("给 Claude 发消息…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            inner()
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    )
                }
                Spacer(Modifier.width(6.dp))
                if (running) {
                    IconButton(onClick = viewModel::interrupt, modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)) {
                        Text("■", color = Color.White, fontSize = 13.sp)
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.sendMessage(text) { text = "" } },
                        enabled = text.isNotBlank() || viewModel.pendingWebAttachment != null,
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(if (text.isNotBlank() || viewModel.pendingWebAttachment != null) AppleBlue else MaterialTheme.colorScheme.surfaceVariant),
                    ) { Icon(Icons.Default.Send, contentDescription = "发送", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPicker(viewModel: AppViewModel) {
    val listing = viewModel.folderListing ?: return
    var directPath by rememberSaveable(listing.path) { mutableStateOf(listing.path) }
    ModalBottomSheet(onDismissRequest = viewModel::dismissFolderPicker, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.76f).padding(horizontal = 18.dp)) {
            Text("选择项目目录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(listing.path, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = directPath,
                    onValueChange = { directPath = it },
                    label = { Text("服务器绝对路径") },
                    placeholder = { Text("例如 /sdc") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = { directPath.trim().takeIf { it.isNotEmpty() }?.let(viewModel::browseFolder) },
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { directPath.trim().takeIf { it.isNotEmpty() }?.let(viewModel::browseFolder) },
                    enabled = directPath.isNotBlank(),
                ) {
                    Text("前往")
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = viewModel::selectCurrentFolder, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("挂载当前目录")
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (listing.locations.isNotEmpty()) {
                    item {
                        Text("快捷位置", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(listing.locations, key = { "location:${it.path}" }) { location ->
                        FolderRow(location.name, location.path) { viewModel.browseFolder(location.path) }
                    }
                    item {
                        Text("当前目录中的文件夹", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                listing.parent?.let { parent ->
                    item {
                        FolderRow("..", parent) { viewModel.browseFolder(parent) }
                    }
                }
                items(listing.directories, key = { it.path }) { directory ->
                    FolderRow(directory.name, directory.path) { viewModel.browseFolder(directory.path) }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, path: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Home, contentDescription = null, tint = AppleBlue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Medium)
                Text(path, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp)
        }
    }
}

@Composable
private fun RemoteFilesScreen(viewModel: AppViewModel) {
    val listing = viewModel.remoteFileListing
    var directPath by rememberSaveable(listing?.path) { mutableStateOf(listing?.path.orEmpty()) }
    LaunchedEffect(Unit) {
        if (listing == null) viewModel.browseRemoteFiles()
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "服务器文件",
            subtitle = listing?.path ?: "浏览服务器用户目录",
            action = {
                Row {
                    listing?.parent?.let { parent ->
                        IconButton(onClick = { viewModel.browseRemoteFiles(parent) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回上级目录")
                        }
                    }
                    IconButton(onClick = viewModel::refreshRemoteFiles, enabled = !viewModel.remoteFilesBusy) {
                        if (viewModel.remoteFilesBusy && listing == null) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新文件")
                        }
                    }
                }
            },
        )
        when {
            listing == null && viewModel.remoteFilesBusy -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text("正在读取服务器文件…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            listing == null -> {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂时无法读取文件", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = viewModel::refreshRemoteFiles, shape = RoundedCornerShape(13.dp)) {
                                Text("重新加载")
                            }
                        }
                    }
                }
            }
            else -> {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = directPath,
                                    onValueChange = { directPath = it },
                                    label = { Text("服务器绝对路径") },
                                    placeholder = { Text("例如 /sdc") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(
                                        onGo = { directPath.trim().takeIf { it.isNotEmpty() }?.let(viewModel::browseRemoteFiles) },
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { directPath.trim().takeIf { it.isNotEmpty() }?.let(viewModel::browseRemoteFiles) },
                                    enabled = directPath.isNotBlank() && !viewModel.remoteFilesBusy,
                                ) {
                                    Text("前往")
                                }
                            }
                        }
                        if (listing.locations.isNotEmpty()) {
                            item {
                                Text("快捷位置", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            items(listing.locations, key = { "location:${it.path}" }) { location ->
                                FolderRow(location.name, location.path) { viewModel.browseRemoteFiles(location.path) }
                            }
                        }
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = AppleBlue.copy(alpha = 0.08f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("⌂", color = AppleBlue, fontSize = 20.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        listing.path,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                        if (listing.entries.isEmpty()) {
                            item {
                                Text(
                                    "这个目录是空的",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                )
                            }
                        }
                        items(listing.entries, key = { it.path }) { entry ->
                            RemoteFileRow(entry) { viewModel.openRemoteFile(entry) }
                        }
                    }
                    if (viewModel.remoteFilesBusy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                            color = AppleBlue,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteFileRow(entry: RemoteFileEntry, onClick: () -> Unit) {
    val color = when {
        entry.isDirectory -> WarmOrange
        entry.isImageFile() -> Color(0xFF6E56CF)
        entry.isVideoFile() -> Color(0xFFFF375F)
        entry.isTextFile() -> AppleBlue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val symbol = when {
        entry.isDirectory -> "▰"
        entry.isImageFile() -> "▧"
        entry.isVideoFile() -> "▶"
        entry.isTextFile() -> "≡"
        else -> "▤"
    }
    Surface(
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(11.dp), color = color.copy(alpha = 0.11f)) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Text(symbol, color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (entry.isDirectory) {
                        "文件夹"
                    } else {
                        listOfNotNull(
                            entry.size.remoteFileSize(),
                            entry.mimeType.takeIf { it.isNotBlank() },
                            entry.modifiedAt.takeIf { it.isNotBlank() }?.replace('T', ' ')?.take(16),
                        ).joinToString(" · ")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteFilePreviewSheet(viewModel: AppViewModel) {
    val entry = viewModel.remoteFilePreview ?: return
    ModalBottomSheet(
        onDismissRequest = viewModel::dismissRemoteFilePreview,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${entry.size.remoteFileSize()} · ${entry.mimeType.ifBlank { "未知格式" }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                IconButton(onClick = viewModel::dismissRemoteFilePreview) {
                    Icon(Icons.Default.Close, contentDescription = "关闭预览")
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = if (entry.isImageFile() || entry.isVideoFile()) Color.Black else MaterialTheme.colorScheme.surface,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        viewModel.remoteFilePreviewBusy -> CircularProgressIndicator()
                        viewModel.remoteFilePreviewError != null -> {
                            Text(
                                viewModel.remoteFilePreviewError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(22.dp),
                            )
                        }
                        entry.isImageFile() && viewModel.remoteFilePreviewBitmap != null -> {
                            Image(
                                bitmap = viewModel.remoteFilePreviewBitmap!!.asImageBitmap(),
                                contentDescription = entry.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        entry.isVideoFile() -> {
                            viewModel.remoteFileContentUrl(entry.path)?.let { url ->
                                Box(Modifier.fillMaxWidth().padding(8.dp)) {
                                    VideoPlayerCard(url, entry.name)
                                }
                            } ?: Text("服务器连接已断开", color = Color.White)
                        }
                        entry.isTextFile() && viewModel.remoteFilePreviewText != null -> {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                            ) {
                                item {
                                    SelectionContainer {
                                        Text(
                                            viewModel.remoteFilePreviewText.orEmpty(),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Column(
                                Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("▤", color = AppleBlue, fontSize = 42.sp)
                                Spacer(Modifier.height(10.dp))
                                Text("此格式暂不支持直接预览", fontWeight = FontWeight.Bold)
                                Text(
                                    "文件仍保存在服务器中",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SelectionContainer {
                Text(
                    entry.path,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun RemoteFileEntry.isImageFile(): Boolean = mimeType.startsWith("image/") &&
    name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

private fun RemoteFileEntry.isVideoFile(): Boolean = mimeType.startsWith("video/") ||
    name.substringAfterLast('.', "").lowercase() in setOf("mp4", "webm", "mov", "m4v", "mkv", "3gp")

private fun RemoteFileEntry.isTextFile(): Boolean = mimeType.startsWith("text/") ||
    name.substringAfterLast('.', "").lowercase() in setOf(
        "txt", "md", "log", "csv", "tsv", "json", "jsonl", "xml", "yaml", "yml",
        "py", "kt", "java", "js", "ts", "tsx", "jsx", "html", "css", "sh", "ini", "cfg",
    )

private fun Long.remoteFileSize(): String = when {
    this < 0L -> "—"
    this >= 1024L * 1024L * 1024L -> "${oneDecimal(this / (1024.0 * 1024.0 * 1024.0))} GB"
    this >= 1024L * 1024L -> "${oneDecimal(this / (1024.0 * 1024.0))} MB"
    this >= 1024L -> "${oneDecimal(this / 1024.0)} KB"
    else -> "$this B"
}

private fun oneDecimal(value: Double): String {
    val tenths = (value * 10.0).roundToInt()
    return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
}

private val NvidiaGreen = Color(0xFF76B900)

@Composable
private fun GpuScreen(viewModel: AppViewModel) {
    LifecycleStartEffect(viewModel) {
        viewModel.startGpuMonitoring()
        onStopOrDispose { viewModel.stopGpuMonitoring() }
    }
    val snapshot = viewModel.gpuSnapshot
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "算力中心",
            subtitle = "GPU 状态与 gpuq 队列 · 每 2 秒更新",
            action = {
                IconButton(onClick = viewModel::refreshGpuStatus, enabled = !viewModel.gpuBusy) {
                    if (viewModel.gpuBusy && snapshot == null) {
                        CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp, color = NvidiaGreen)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新算力与队列状态", tint = NvidiaGreen)
                    }
                }
            },
        )
        when {
            snapshot == null && viewModel.gpuBusy -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NvidiaGreen)
                        Spacer(Modifier.height(12.dp))
                        Text("正在读取算力与队列状态…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            snapshot == null -> GpuUnavailableCard(viewModel.gpuError ?: "暂时没有读取到服务器算力状态")
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { GpuQueueCard(snapshot.queue) }
                    if (snapshot.available) {
                        item { GpuSummaryCard(snapshot) }
                    } else {
                        item {
                            GpuTelemetryUnavailableCard(
                                snapshot.message ?: "这台服务器没有可用的 NVIDIA GPU",
                            )
                        }
                    }
                    viewModel.gpuError?.let { message ->
                        item {
                            Text(
                                "最近一次刷新失败：$message",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    if (snapshot.available) {
                        items(snapshot.gpus, key = { it.uuid.ifBlank { it.index.toString() } }) { gpu ->
                            GpuDeviceCard(gpu, snapshot.processesAvailable)
                        }
                    }
                    item {
                        Text(
                            "本页只读调用服务器本机 nvidia-smi 与 gpuq list，不会提交、取消或修改队列任务，也不调用 Claude 或 DeepSeek API；离开页面后自动停止刷新。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GpuUnavailableCard(message: String) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = NvidiaGreen.copy(alpha = 0.14f)) {
                    Text("算力", color = NvidiaGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("算力状态不可用", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GpuTelemetryUnavailableCard(message: String) {
    Surface(shape = RoundedCornerShape(23.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = NvidiaGreen.copy(alpha = 0.14f)) {
                Text(
                    "GPU",
                    color = NvidiaGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("NVIDIA 遥测不可用", fontWeight = FontWeight.Bold)
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GpuQueueCard(queue: GpuQueueSnapshot) {
    val running = queue.jobs.count { it.status == "running" }
    val queued = queue.jobs.count { it.status == "queued" }
    Surface(shape = RoundedCornerShape(23.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = NvidiaGreen.copy(alpha = 0.14f)) {
                    Text(
                        "gpuq",
                        color = NvidiaGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("任务队列", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "只显示 gpuq list 中正在运行和等待的任务",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                }
                Text(
                    queue.jobs.size.toString(),
                    color = NvidiaGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp,
                )
            }
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                GpuMetricChip("运行中", running.toString(), NvidiaGreen, Modifier.weight(1f))
                GpuMetricChip("排队中", queued.toString(), WarmOrange, Modifier.weight(1f))
                GpuMetricChip("活动任务", queue.jobs.size.toString(), AppleBlue, Modifier.weight(1f))
            }
            HorizontalDivider(
                Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            )
            when {
                !queue.available -> Text(
                    queue.message ?: "gpuq 队列当前不可用",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                queue.jobs.isEmpty() -> Text(
                    "当前没有正在运行或排队中的 gpuq 任务",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                else -> queue.jobs.forEachIndexed { index, job ->
                    if (index > 0) Spacer(Modifier.height(9.dp))
                    GpuQueueJobCard(job)
                }
            }
        }
    }
}

@Composable
private fun GpuQueueJobCard(job: GpuQueueJob) {
    val isRunning = job.status == "running"
    val statusColor = if (isRunning) NvidiaGreen else WarmOrange
    val statusLabel = when (job.status) {
        "running" -> "运行中"
        "queued" -> "排队中"
        else -> job.status.ifBlank { "未知" }
    }
    val gpuPlacement = if (job.gpuIndices.isBlank()) "待分配" else job.gpuIndices
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = statusColor.copy(alpha = 0.075f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(7.dp))
                Text(statusLabel, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text("#${job.id}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                job.name.ifBlank { "任务 ${job.id}" },
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                buildList {
                    add("GPU $gpuPlacement")
                    add("申请 ${job.gpuCount} 张")
                    job.pid?.let { add("PID $it") }
                }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
            Text(
                "已等待 ${job.waited.ifBlank { "—" }} · 已运行 ${job.running.ifBlank { "—" }} · 优先级 ${job.priority}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun GpuSummaryCard(snapshot: GpuSnapshot) {
    val gpus = snapshot.gpus
    val averageUtil = gpus.mapNotNull { it.gpuUtilizationPercent }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val usedMemory = gpus.mapNotNull { it.memoryUsedMiB }.sum()
    val totalMemory = gpus.mapNotNull { it.memoryTotalMiB }.sum()
    val totalPower = gpus.mapNotNull { it.powerDrawW }.sum()
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF101510), Color(0xFF1D2B13), Color(0xFF111411))
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(NvidiaGreen))
                Spacer(Modifier.width(8.dp))
                Text("LIVE", color = NvidiaGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    snapshot.timestamp.substringAfter('T', snapshot.timestamp).take(8),
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("${gpus.size} × NVIDIA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text(
                "驱动 ${snapshot.driverVersion.ifBlank { "未知" }}",
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GpuSummaryMetric(averageUtil?.let { "${it.roundToInt()}%" } ?: "—", "平均负载")
                GpuSummaryMetric(
                    if (totalMemory > 0f) "${usedMemory.gibText()} / ${totalMemory.gibText()}" else "—",
                    "显存 GiB",
                )
                GpuSummaryMetric(
                    if (totalPower > 0f) "${totalPower.roundToInt()} W" else "—",
                    "总功耗",
                )
            }
        }
    }
}

@Composable
private fun GpuSummaryMetric(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(label, color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp)
    }
}

@Composable
private fun GpuDeviceCard(gpu: GpuInfo, processesAvailable: Boolean) {
    val gpuLoad = gpu.gpuUtilizationPercent?.coerceIn(0f, 100f)
    val memoryRatio = if (
        gpu.memoryUsedMiB != null && gpu.memoryTotalMiB != null && gpu.memoryTotalMiB > 0f
    ) {
        (gpu.memoryUsedMiB / gpu.memoryTotalMiB * 100f).coerceIn(0f, 100f)
    } else {
        gpu.memoryUtilizationPercent?.coerceIn(0f, 100f)
    }
    val temperatureColor = if ((gpu.temperatureC ?: 0f) >= 80f) WarmOrange else NvidiaGreen
    Surface(shape = RoundedCornerShape(23.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = NvidiaGreen.copy(alpha = 0.14f)) {
                    Text(
                        "GPU ${gpu.index}",
                        color = NvidiaGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(gpu.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            gpu.performanceState,
                            gpu.driverVersion.takeIf { it.isNotBlank() }?.let { "驱动 $it" },
                        ).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                }
                Text(
                    gpuLoad?.let { "${it.roundToInt()}%" } ?: "—",
                    color = NvidiaGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            GpuMetricBar("核心负载", gpuLoad?.let { "${it.roundToInt()}%" } ?: "不可用", gpuLoad, NvidiaGreen)
            Spacer(Modifier.height(11.dp))
            GpuMetricBar(
                "显存",
                if (gpu.memoryUsedMiB != null && gpu.memoryTotalMiB != null) {
                    "${gpu.memoryUsedMiB.gibText()} / ${gpu.memoryTotalMiB.gibText()} GiB"
                } else {
                    "不可用"
                },
                memoryRatio,
                AppleBlue,
            )
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                GpuMetricChip(
                    "温度",
                    gpu.temperatureC?.let { "${it.roundToInt()}°C" } ?: "—",
                    temperatureColor,
                    Modifier.weight(1f),
                )
                GpuMetricChip(
                    "功耗",
                    gpu.powerDrawW?.let { "${it.roundToInt()} W" } ?: "—",
                    WarmOrange,
                    Modifier.weight(1f),
                )
                GpuMetricChip(
                    "风扇",
                    gpu.fanSpeedPercent?.let { "${it.roundToInt()}%" } ?: "—",
                    Color(0xFF6E8EF7),
                    Modifier.weight(1f),
                )
            }
            if (gpu.graphicsClockMHz != null || gpu.memoryClockMHz != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "核心 ${gpu.graphicsClockMHz?.roundToInt()?.let { "$it MHz" } ?: "—"}  ·  " +
                        "显存 ${gpu.memoryClockMHz?.roundToInt()?.let { "$it MHz" } ?: "—"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
            HorizontalDivider(
                Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            )
            Text("计算进程", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            when {
                !processesAvailable -> Text(
                    "当前驱动未提供进程信息",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                gpu.processes.isEmpty() -> Text(
                    "当前没有占用显存的计算进程",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                else -> gpu.processes.forEach { process ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(process.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("PID ${process.pid}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                        }
                        Text(
                            process.memoryUsedMiB?.let { "${it.gibText()} GiB" } ?: "—",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GpuMetricBar(label: String, value: String, percent: Float?, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        LinearProgressIndicator(
            progress = { ((percent ?: 0f) / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(7.dp).clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.13f),
        )
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
    }
}

@Composable
private fun GpuMetricChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(13.dp), color = color.copy(alpha = 0.10f)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

private fun Float.gibText(): String {
    val tenths = (this / 1024f * 10f).roundToInt()
    return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserScreen(viewModel: AppViewModel) {
    var address by remember { mutableStateOf(DEFAULT_BROWSER_URL) }
    var pageTitle by remember { mutableStateOf("网页检索") }
    var progress by remember { mutableStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var ocrBusy by remember { mutableStateOf(false) }
    var ocrCurrent by remember { mutableStateOf(0) }
    var ocrTotal by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun openAddress() {
        val target = normalizeAddress(address)
        address = target
        pageError = null
        webView?.loadUrl(target)
    }

    fun refreshAddress() {
        val target = normalizeAddress(address)
        address = target
        pageError = null
        val view = webView
        if (view?.url == target) view.reload() else view?.loadUrl(target)
    }

    fun openQuick(url: String) {
        address = url
        pageError = null
        webView?.loadUrl(url)
    }

    fun attachUsingOcr(pagesFromCurrent: Int?) {
        val view = webView
        if (view == null) {
            viewModel.showError("网页尚未准备好")
            return
        }
        if (ocrBusy) return
        scope.launch {
            ocrBusy = true
            ocrCurrent = 0
            ocrTotal = 0
            try {
                val page = extractWebPageWithOcr(
                    webView = view,
                    fallbackTitle = pageTitle,
                    fallbackUrl = address,
                    pagesFromCurrent = pagesFromCurrent,
                    onProgress = { current, total ->
                        ocrCurrent = current
                        ocrTotal = total
                    },
                )
                viewModel.showOcrPreview(page.title, page.url, page.content)
            } catch (error: Throwable) {
                if (error !is CancellationException) {
                    viewModel.showError(error.message?.takeIf { it.isNotBlank() } ?: "网页 OCR 提取失败")
                }
            } finally {
                ocrBusy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("网页检索", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("搜索网页，通过本地中英 OCR 附加给 Claude", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "后退")
                }
                IconButton(onClick = ::refreshAddress) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(17.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 1.dp,
            ) {
                BasicTextField(
                    address,
                    { address = it },
                    modifier = Modifier.fillMaxWidth().padding(start = 15.dp, end = 7.dp, top = 11.dp, bottom = 11.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { openAddress() }),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = AppleBlue)
                            Spacer(Modifier.width(9.dp))
                            Box(Modifier.weight(1f)) {
                                if (address.isBlank()) Text("输入网址或搜索关键词", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            }
                            if (address.isNotBlank()) {
                                IconButton(onClick = { address = "" }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = ::openAddress, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Search, contentDescription = "打开或搜索", tint = AppleBlue, modifier = Modifier.size(19.dp))
                            }
                        }
                    },
                )
            }
        }
        if (progress in 1..99) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(progress / 100f).height(2.dp).background(AppleBlue))
            }
        }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            shadowElevation = 2.dp,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(android.graphics.Color.WHITE)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    pageError = null
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    address = url
                                    pageTitle = view.title ?: url
                                }

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    if (request?.isForMainFrame == true) {
                                        pageError = "网页加载失败，请检查手机网络或换一个网址。"
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                                override fun onReceivedTitle(view: WebView?, title: String?) { if (!title.isNullOrBlank()) pageTitle = title }
                            }
                            loadUrl(address)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (pageError != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp,
                    ) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("无法显示网页", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(pageError.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                            FilledTonalButton(onClick = ::openAddress) { Text("重新加载") }
                        }
                    }
                }
                DraggableOcrAttachButton(
                    busy = ocrBusy,
                    current = ocrCurrent,
                    total = ocrTotal,
                    onScan = ::attachUsingOcr,
                    modifier = Modifier.fillMaxSize(),
                )
                DraggableShortcutMenu(
                    onOpen = ::openQuick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DraggableOcrAttachButton(
    busy: Boolean,
    current: Int,
    total: Int,
    onScan: (pagesFromCurrent: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val bubbleSize = 62.dp
        val panelWidth = 184.dp
        val panelHeight = 246.dp
        val margin = 12.dp
        val bubblePx = with(density) { bubbleSize.toPx() }
        val panelWidthPx = with(density) { panelWidth.toPx() }
        val panelHeightPx = with(density) { panelHeight.toPx() }
        val marginPx = with(density) { margin.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val maxX = (widthPx - bubblePx - marginPx).coerceAtLeast(marginPx)
        val maxY = (heightPx - bubblePx - marginPx).coerceAtLeast(marginPx)
        var x by remember(maxX, maxY) { mutableStateOf(maxX) }
        var y by remember(maxX, maxY) { mutableStateOf(maxY) }
        var expanded by remember { mutableStateOf(false) }

        Surface(
            onClick = { expanded = !expanded },
            enabled = !busy,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(bubbleSize)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        x = (x + dragAmount.x).coerceIn(marginPx, maxX)
                        y = (y + dragAmount.y).coerceIn(marginPx, maxY)
                    }
                }
                .semantics { contentDescription = "选择 OCR 页数并附加给 Claude，可拖动" },
            shape = CircleShape,
            color = Color(0xF21C1C1E),
            shadowElevation = 9.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (busy) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                        if (total > 0) Text("$current/$total", color = Color.White, fontSize = 8.sp)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("＋", color = Color.White, fontSize = 21.sp, lineHeight = 19.sp)
                        Text("OCR 附加", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (expanded && !busy) {
            val panelX = (x + bubblePx - panelWidthPx).coerceIn(
                marginPx,
                (widthPx - panelWidthPx - marginPx).coerceAtLeast(marginPx),
            )
            val below = y + bubblePx + with(density) { 7.dp.toPx() }
            val panelY = if (below + panelHeightPx <= heightPx - marginPx) {
                below
            } else {
                (y - panelHeightPx - with(density) { 7.dp.toPx() }).coerceAtLeast(marginPx)
            }
            Surface(
                modifier = Modifier.offset { IntOffset(panelX.roundToInt(), panelY.roundToInt()) }.width(panelWidth),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shadowElevation = 10.dp,
            ) {
                Column(Modifier.padding(vertical = 7.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("OCR 范围", fontWeight = FontWeight.SemiBold)
                            Text("从当前浏览位置开始", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { expanded = false }, modifier = Modifier.height(30.dp)) { Text("收起", fontSize = 10.sp) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    OcrRangeItem("仅当前页") { expanded = false; onScan(1) }
                    OcrRangeItem("当前页到后 1 页") { expanded = false; onScan(2) }
                    OcrRangeItem("当前页到后 3 页") { expanded = false; onScan(4) }
                    OcrRangeItem("当前页到后 5 页") { expanded = false; onScan(6) }
                    OcrRangeItem("当前页一直到末页") { expanded = false; onScan(null) }
                }
            }
        }
    }
}

@Composable
private fun OcrRangeItem(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(36.dp)) {
        Text(label, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}

@Composable
private fun DraggableShortcutMenu(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val bubbleSize = 56.dp
        val panelWidth = 164.dp
        val panelHeight = 222.dp
        val margin = 12.dp
        val bubblePx = with(density) { bubbleSize.toPx() }
        val panelWidthPx = with(density) { panelWidth.toPx() }
        val panelHeightPx = with(density) { panelHeight.toPx() }
        val marginPx = with(density) { margin.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val maxX = (widthPx - bubblePx - marginPx).coerceAtLeast(marginPx)
        val maxY = (heightPx - bubblePx - marginPx).coerceAtLeast(marginPx)
        var x by remember(maxX, maxY) { mutableStateOf(maxX) }
        var y by remember(maxX, maxY) { mutableStateOf(marginPx) }
        var expanded by remember { mutableStateOf(false) }

        Surface(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(bubbleSize)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        x = (x + dragAmount.x).coerceIn(marginPx, maxX)
                        y = (y + dragAmount.y).coerceIn(marginPx, maxY)
                    }
                }
                .semantics { contentDescription = "快捷网页，可拖动" },
            shape = CircleShape,
            color = AppleBlue.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("◎", color = Color.White, fontSize = 19.sp, lineHeight = 18.sp)
                Text("快捷", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (expanded) {
            val panelX = (x + bubblePx - panelWidthPx).coerceIn(
                marginPx,
                (widthPx - panelWidthPx - marginPx).coerceAtLeast(marginPx),
            )
            val below = y + bubblePx + with(density) { 7.dp.toPx() }
            val panelY = if (below + panelHeightPx <= heightPx - marginPx) {
                below
            } else {
                (y - panelHeightPx - with(density) { 7.dp.toPx() }).coerceAtLeast(marginPx)
            }
            Surface(
                modifier = Modifier.offset { IntOffset(panelX.roundToInt(), panelY.roundToInt()) }.width(panelWidth),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                shadowElevation = 10.dp,
            ) {
                Column(Modifier.padding(vertical = 7.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("快捷网页", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { expanded = false }, modifier = Modifier.height(30.dp)) { Text("收起", fontSize = 10.sp) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ShortcutMenuItem("SB3 文档") { expanded = false; onOpen(SB3_DOCS_URL) }
                    ShortcutMenuItem("Smol Course") { expanded = false; onOpen(SMOL_COURSE_URL) }
                    ShortcutMenuItem("HF 文档") { expanded = false; onOpen(HF_DOCS_URL) }
                    ShortcutMenuItem("RAG 教程") { expanded = false; onOpen(RAG_TUTORIAL_URL) }
                }
            }
        }
    }
}

@Composable
private fun ShortcutMenuItem(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(36.dp)) {
        Text(label, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}

private fun normalizeAddress(input: String): String {
    val value = input.trim()
    return when {
        value.isBlank() -> DEFAULT_BROWSER_URL
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.contains('.') && !value.contains(' ') -> "https://$value"
        else -> "https://www.bing.com/search?mkt=zh-CN&q=" + URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}

private const val DEFAULT_BROWSER_URL = "https://www.bing.com/?mkt=zh-CN"
private const val SB3_DOCS_URL = "https://stable-baselines3.readthedocs.io/en/master/"
private const val SMOL_COURSE_URL = "https://huggingface.co/learn/smol-course/unit0/1"
private const val HF_DOCS_URL = "https://huggingface.co/docs"
private const val RAG_TUTORIAL_URL = "https://vivy-yi.github.io/rag-tutorial/"
