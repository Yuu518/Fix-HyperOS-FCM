# HyperOS GMS Keeper

An LSPosed module that runs only in Android's `system_server` process. It prevents
HyperOS Aurogon/Greeze from freezing the entire GMS UID after the screen has been
off for an extended period, which would also remove FCM heartbeat, queue-check,
and idle-reconnect alarms.

## Root Cause and Hook Scope

On a Xiaomi HyperOS `OS4` (Android 17), the ROM
calls the following method 60 seconds after the screen turns off:

```text
com.miui.server.greeze.GreezeManagerService.triggerGMSLimitAction(): void
```

This method runs only when the device uses the China region, GMS limiting is
enabled, and the screen is off. On the tested ROM, it first removes
`com.google.android.gms` from the Aurogon allowlist and then calls
`triggerQuickFreeze(uid, 0)` for each running GMS UID across all users. The
subsequent transaction freezes the UID, disables its network state, and causes
AlarmManager to remove alarms belonging to that UID.

This module returns before that dedicated GMS-limiting entry point executes. It
does not hook the general-purpose freezer or modify power-saving policies for
other apps. This prevents the same transaction from removing the allowlist entry,
freezing the GMS UID, and deleting FCM alarms.

## Notifications Vanishing from the Status Bar and Lock Screen

Some apps, notably Telegram and its forks, re-post an existing notification into
a low-importance "silent" channel when they sync after an FCM wake-up. The
notification then becomes silent, and HyperOS SystemUI:

- drops silent notifications from the status bar icons, and
- hides them from the lock screen, because it reads
  `lock_screen_show_silent_notifications` with a default of `false`.

The notification is still in the shade, so it looks as if the message was
swallowed. The module hooks the AOSP method

```text
com.android.server.notification.NotificationRecord.copyRankingInformation(NotificationRecord): void
```

which `NotificationManagerService` calls only when an app updates a notification
that is already posted. If the update moves the notification from a channel with
importance `DEFAULT` or higher to a different channel below `DEFAULT`, the module
keeps the previous channel through `updateSystemNotificationChannel`, adds
`FLAG_ONLY_ALERT_ONCE`, and marks the record to post silently. The update still
makes no sound, vibration, or heads-up, but the notification keeps its status bar
icon and lock screen entry.

The hook leaves the notification unchanged when:

- it is a new post rather than an update,
- the new channel's importance was set by the user,
- the new channel is blocked, or
- the notification is ongoing, a foreground service, or a user-initiated job.

## Installation

1. Install the generated APK. With a device connected, you can run:

   ```powershell
   adb install -r .\app\build\outputs\apk\debug\HyperOS-GMS-Keeper-1.3.0-debug.apk
   ```

2. Make sure your LSPosed implementation supports libxposed API 102, then enable
   the module. Its scope is statically set to `system` (`system_server`).
3. Reboot the device. Force-stopping an app is not enough to load a
   `system_server` hook for the first time.

### Updating

The module opts into libxposed API 102 hot reload (`autoHotReload=true`). Once a
hot-reload-capable version is running in `system_server`, installing a newer APK
with `adb install -r` makes LSPosed swap the hook in place without a reboot; the
old hook is atomically replaced, so there is no window in which GMS limiting can
slip through. Updating from a version without hot reload support, or an
LSPosed build that reports hot reload as unsupported, still requires a reboot.

The module has no launcher entry and does not modify system files. Each hook is
resolved independently. If a class or method above does not exist on the current
ROM, the module only records that hook's failure in the LSPosed log and still
installs the other one; it does not fall back to intercepting freeze operations
for every app.

## Verification

First, confirm these messages appear in the LSPosed log:

```text
HyperOSGmsKeeper: hook installed in system_server; scope is limited to system
HyperOSGmsKeeper: notification channel hook installed in system_server
```

After a hot reload, the new version logs this for each hook instead:

```text
HyperOSGmsKeeper: hook block-gms-limit reinstalled in system_server after hot reload
HyperOSGmsKeeper: hook keep-alerting-channel reinstalled in system_server after hot reload
```

When an app tries to move a posted notification into a silent channel, the
module logs the notification key and both channel IDs:

```text
HyperOSGmsKeeper: kept alerting channel for 0|org.telegram.messenger|12|null|10234: silent -> messages
```

After the screen has remained off longer than the ROM's original trigger delay,
the following message should appear:

```text
HyperOSGmsKeeper: blocked GreezeManagerService.triggerGMSLimitAction()
```

The GMS UID should no longer be frozen by Greeze/cgroup, and the FCM connection
to port `5228` should remain connected or reconnect automatically after a
disconnect. The GMS hook only addresses the upstream GMS connection lifecycle.
Notification delays caused by restrictions on the target app still need to be
handled separately.

## Building

JDK 17 or 21 and Android SDK Platform 34 are required. JDK 25 is currently
incompatible with the Gradle/Android Gradle Plugin versions used by this project.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

### GitHub Actions Release Signing

The Actions workflow builds a non-debuggable, signed release APK when all of
the following secrets are available. Without them (for example on forks and
pull requests from forks), it runs the unit tests and uploads a debug APK
instead. Add the secrets under `Settings -> Secrets and variables -> Actions`
in the repository:

- `RELEASE_KEYSTORE_BASE64`: Base64-encoded signing keystore
- `RELEASE_STORE_PASSWORD`: Keystore password
- `RELEASE_KEY_ALIAS`: Key alias
- `RELEASE_KEY_PASSWORD`: Key password

To create a dedicated signing keystore for the first release:

```powershell
keytool -genkeypair -v -keystore release.jks -alias hyperos-gms-keeper -keyalg RSA -keysize 4096 -validity 10000
```

To copy the Base64-encoded keystore to the clipboard in PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```

Do not commit `release.jks`; `.gitignore` already excludes `*.jks` and
`*.keystore`. Back up the same keystore after the first release because all
future APKs must use the same signing key to support in-place updates.
