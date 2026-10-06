# Fix-HyperOS-FCM

移除 HyperOS 国行对 FCM 推送施加的冻结与广播延迟限制的 LSPosed 模块。

- 作用域：系统框架（`system`）
- Xposed API：libxposed 102，支持热重载（更新模块后不必重启）
- 已验证环境：HyperOS OS4.0.0.43.XPBCNXM（Android 17）、LSPosed v2.2.1

## 移除的限制

以下限制都在 `system_server` 的 `com.miui.server.greeze` 中，国际版没有或默认放行：

| 方法 | 国行行为 | 模块处理 |
|------|----------|----------|
| `GreezeManagerService.isAllowBroadcast` | 熄屏时发给被冻结应用的 `c2dm.intent.RECEIVE` 被拒，应用不会被解冻 | 对 FCM 推送和 GCM 连接广播放行 |
| `GreezeManagerService.triggerGMSLimitAction` | 熄屏后把 GMS 移出白名单并冻结 | 不执行 |
| `AurogonImmobulusMode.triggerQuickFreeze` | GMS 被解冻后熄屏 5 秒再次冻结 | 对 GMS 跳过 |
| `GreezeManagerService.updateGmsNetStatus` | 省电与电池探测到 Google 不可达时启用 GMS 限制 | 始终按「不限制」处理 |
| `DomesticPolicyManager.deferBroadcast` | 熄屏时延迟 GCM 心跳、重连、连接状态广播 | 不再延迟 |

## 保留的限制

模块不改动自启动相关的行为：

- 未授权自启动的应用不会被 FCM 拉起（`BroadcastQueueModernStubImpl.checkApplicationAutoStart`）。
- 无自启动权限的应用被划卡杀后台时仍会被强行停止（`ProcessManagerService.isForceStopEnable`）。

模块只会让「进程还在但被冻结」的应用在 FCM 到达时解冻，不会启动任何进程。想让某个应用在被杀后仍能收到推送，需要在系统设置里给它自启动权限。

## 安装

1. 从 Actions 的最近一次 Build 运行中下载 APK 并安装。
2. 在 LSPosed 中启用模块，作用域勾选「系统框架」。
3. 首次启用后重启手机。之后更新模块会自动热重载。

## 验证

```sh
adb logcat -s HyperOSFCM
```

正常时会看到 `Installed 5/5 hooks`。熄屏 30 秒后执行下面的命令，不应再出现 `gmsUids size` 的日志：

```sh
adb logcat -d | grep triggerGMSLimitAction
```

如果某个 hook 安装失败，日志会写明是哪一个；其余 hook 不受影响。这通常说明当前 ROM 版本改动了对应的方法。

## 构建

只通过 GitHub Actions 构建，见 `.github/workflows/build.yml`。推送到 `master` 或手动触发时，会运行单元测试、构建并签名 release 包，APK 作为 workflow artifact 上传，保留 14 天。不构建 debug 包，也不创建 GitHub Release。

仓库需要配置以下 Secrets：

| Secret | 内容 |
|--------|------|
| `ANDROID_KEYSTORE_BASE64` | 密钥库文件的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |

每次构建必须使用同一个密钥，否则无法覆盖安装，也无法热重载。
