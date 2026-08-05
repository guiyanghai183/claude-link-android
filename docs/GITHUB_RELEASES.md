# GitHub 发布与应用内更新

仓库：`https://github.com/guiyanghai183/claude-link-android`

Claude Link 启动时会读取：

`https://github.com/guiyanghai183/claude-link-android/releases/latest/download/latest.json`

若清单中的 `versionCode` 大于已安装版本，应用会显示更新弹窗。下载完成后先校验 SHA-256，再交给 Android 系统安装器；Android 仍会要求用户确认安装，并验证新 APK 与已安装应用的签名关系。

## 一次性配置发布签名

发布 APK 必须始终使用同一把私钥。丢失私钥后，已安装用户无法原地升级，因此请至少准备两份离线备份。

1. 安装 JDK 17 与 GitHub CLI，并执行 `gh auth login`。Windows 可使用：

   ```powershell
   winget install --id GitHub.cli
   gh auth login
   ```
2. 在项目根目录运行：

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\create-release-key.ps1
   ```

3. 将 `release-signing/` 目录安全备份。该目录已被 `.gitignore` 排除。
4. 把签名信息写入 GitHub Actions Secrets：

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\configure-github-secrets.ps1
   ```

脚本会配置以下 Secrets，不会把明文写入仓库：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## 发布新版本

1. 同时递增 `android/app/build.gradle.kts` 中的 `versionCode` 和 `versionName`。
2. 提交并推送代码，确认 CI 通过。
3. 创建与 `versionName` 完全一致的标签，例如版本为 `0.3.4`：

   ```powershell
   git tag v0.3.4
   git push origin v0.3.4
   ```

标签会触发 `Release APK` 工作流，自动完成：

- 服务器桥接单元测试；
- 使用仓库 Secrets 构建并签名 release APK；
- 核对标签与应用版本；
- 校验 APK 签名；
- 创建 GitHub Release；
- 上传 APK、`SHA256SUMS.txt` 和供应用读取的 `latest.json`。

## 从当前调试版迁移

当前手机上的 `0.1.2-debug` 使用 Android 调试签名。首个正式 Release 会使用新生成的发布密钥，因此 Android 不允许直接覆盖调试版。首次迁移需要卸载调试版，再安装 GitHub Release 中的正式 APK；服务器聊天历史和项目文件不受影响，但手机端服务器配置需要重新添加一次。之后所有 GitHub Release 都能原地更新。
