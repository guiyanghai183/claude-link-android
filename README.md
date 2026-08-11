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
- 服务器目录浏览与项目目录挂载：可直接输入 `/sdc` 等绝对路径，并快速进入主目录、根目录和可访问的已挂载文件系统。
- 聊天历史默认保留 7 天；星标对话永久保存。
- PNG、JPG、WebP、GIF、SVG、PDF、CSV 等实验产物自动发现，图片直接显示在聊天中。
- Claude 可调用内置媒体工具，把真实视频文件或直链稳定交付到聊天播放器；旧文本链接协议仍兼容。
- 每个项目同一时间只运行一个 Claude 回合，消息带幂等 ID，会话切换和项目切换相互隔离。
- 服务器文件浏览器支持当前 SSH 用户有权读取的全文件系统目录、隐藏项、图片、视频与常见文本文件直接预览。
- 消息栏支持免费的 Android 系统语音转文字，并会贴合输入法高度。
- DeepSeek API 余额查询与健身环式用量展示；密钥只加密保存在手机。
- 内置 WebView 浏览器、加长地址/搜索栏、可拖动快捷网页浮球和可拖动 OCR 附加浮球。
- 网页会从当前浏览位置按所选页数逐屏截图，并通过中文与英文双模型 OCR 合并提取后附加给 Claude。
- 附件仅在服务器确认接收后清除；聊天消息会显示 Claude 实际收到的 OCR 标题和字数。
- GitHub Releases 应用内更新：启动自动检查、发布说明、SHA-256 校验和系统安装确认。
- 浅色/深色苹果风格 Compose 界面。

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
