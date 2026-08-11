# Claude Link

Claude Link 是一个通过局域网 SSH 安全控制服务器 Claude Code 的 Android 客户端。

GitHub 仓库：<https://github.com/guiyanghai183/claude-link-android>

## 已实现

- 多服务器管理：IP、端口、用户名和首次登录密码。
- 首次连接后生成手机专用 RSA 密钥，私钥由 Android Keystore 加密保存，服务器密码不落盘。
- SSH 主机密钥首次固定，后续连接检测服务器身份变化。
- SSH 本地端口转发；配套服务只监听服务器 `127.0.0.1:18765`。
- VPN 网络优先路由、SSH 保活和断线自动恢复。
- Claude Code 流式 JSON 多轮会话、状态同步、中断和断线后恢复。
- Claude Code 终端操作审批：手机显示工具、命令和影响路径，可选择本次允许或拒绝。
- 服务器目录浏览与项目目录挂载：快捷入口只显示 SSH 用户主目录和 `/` 根目录，可逐级浏览，也可直接输入 `/sdc` 等绝对路径挂载。
- 对话页支持两种会话：选择服务器目录后，可创建 Claude 对话或直接进入该目录的 SSH 远程终端对话。
- 远程终端使用持续 PTY 会话，支持实时输出、`Ctrl+C`、`Ctrl+D`、命令历史和中文输入法整段提交；交互程序输入不会写入聊天记录。
- 聊天历史默认保留 7 天；星标对话永久保存。
- PNG、JPG、WebP、GIF、SVG、PDF、CSV 等实验产物自动发现，图片直接显示在聊天中。
- Claude 可调用内置媒体工具，把真实视频文件或直链稳定交付到聊天播放器；旧文本链接协议仍兼容。
- 每个项目同一时间只运行一个 Claude 回合，消息带幂等 ID，会话切换和项目切换相互隔离。
- 服务器文件浏览器支持当前 SSH 用户有权读取的全文件系统目录、隐藏项、图片、视频与常见文本文件直接预览。
- 消息栏在应用内调用 Android 系统语音转文字：Android 12 及以上优先使用本机识别器，其他设备请求离线模式，不再打开外部 Google 语音页面；缺少离线模型时会明确提示。
- DeepSeek API 余额查询与健身环式用量展示；密钥只加密保存在手机。
- 内置 WebView 浏览器、加长地址/搜索栏、可拖动快捷网页浮球和可拖动 OCR 附加浮球。
- 网页会从当前浏览位置按所选页数逐屏截图，并通过中文与英文双模型 OCR 合并提取后附加给 Claude。
- 附件仅在服务器确认接收后清除；聊天消息会显示 Claude 实际收到的 OCR 标题和字数。
- GitHub Releases 应用内更新：启动自动检查、发布说明、SHA-256 校验和系统安装确认。
- 浅色/深色苹果风格 Compose 界面。

## 远程终端对话

1. 连接服务器后进入“对话”，点击右上角 `+`。
2. 从 `gyhai` 或 `/` 根目录逐级选择，也可直接输入 `/sdc` 等绝对路径。
3. 点击“挂载当前目录”，然后选择“Claude 对话”或“远程终端对话”。
4. 终端对话通过现有安全 SSH 连接在服务器上打开 PTY，并首先进入所选目录；它不会在 Android 手机上执行本地命令。

终端空闲时，输入框提交的是一条服务器命令，命令和输出会保存在对应终端对话中。远程程序尚未结束时，输入框会切换为标准输入模式；此时内容直接送入远程程序但不保存，避免记录 `sudo` 密码等敏感输入。所有文本先由 Android 输入法完成拼音、候选词或语音输入，再以 UTF-8 整段发送到服务器。

终端底部采用紧凑输入栏，只保留 SSH 状态、`^C`、`^D`、命令历史、输入框和发送键；当前目录仍显示在对话顶部，不在输入栏重复展示。

终端对话适合普通 Shell、脚本、Python REPL 和需要标准输入的命令。聊天式输出会清理 ANSI 控制码，但不完整模拟 `vim`、`top` 等全屏 VT 终端界面。

## 项目结构

- `android/`：Kotlin + Jetpack Compose Android 应用。
- `server/`：Python 标准库实现的服务器桥接服务及单元测试。
- `server/install.sh`：可选的 Linux 用户级 systemd 安装脚本。

## 构建

需要 JDK 17、Android SDK 36 和 Build Tools 35.0.0。

```powershell
cd android
./gradlew.bat :app:assembleDebug
```

服务器端不需要额外 Python 包。应用会通过 SFTP 校验并同步内置的 `mobile_claude_server.py`，也可以手动执行：

```bash
cd server
bash install.sh mobile_claude_server.py
```

## 数据保留

服务端 SQLite 历史位于 `~/.local/share/mobile-claude/history.sqlite3`。未星标对话按最后活动时间保留 7 天；清理时也会删除对应的 Claude 会话记录，但不会删除项目中的实验文件。星标对话不参与自动清理。

## GitHub 自动发布

推送形如 `v0.3.4` 的版本标签后，GitHub Actions 会测试项目、使用仓库 Secrets 签名 release APK，并创建包含 APK、SHA-256 和 `latest.json` 的 GitHub Release。首次配置与发布步骤见 [`docs/GITHUB_RELEASES.md`](docs/GITHUB_RELEASES.md)。
