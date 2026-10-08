# Touchless

## 中文

Touchless 是一个实验中的 Android 手势控制应用，使用前置摄像头和在设备本地运行的 MediaPipe 手部关键点模型。手部向上移动可触发一次屏幕上滑；可选择启用握拳返回桌面，以及单食指光标与轻弯点击。悬浮球用于暂停和继续控制，识别参数、主题与中英文界面可在应用内设置。

**状态：**源码版本 0.8.0。尚未完成全面真机测试；本仓库不提供经过验证的安装包。识别效果、耗电和后台运行情况取决于设备与系统设置。

### 构建

1. 使用 Android Studio 打开本仓库根目录，安装项目要求的 Android SDK 和 JDK。
2. 等待 Gradle 同步，运行 `./gradlew assembleDebug`（Windows 使用 `./gradlew.bat assembleDebug`）。
3. 首次构建需要联网下载依赖与约 8 MB 的手部模型。模型保存在本地构建目录对应的源码资产位置，不纳入 Git 仓库；安装后的识别在设备本地完成。
4. Debug APK 位于 `app/build/outputs/apk/debug/`。正式分发前需自行签名 Release APK 并验证权限、功能和安装行为。

### 权限和隐私

应用使用相机进行本地手部识别、悬浮窗显示控制球、无障碍服务执行用户触发的操作，以及前台服务通知。源码在 Android 清单中移除依赖传递引入的联网权限；发布前仍应检查最终 APK。摄像头画面不保存或上传。权限用途以应用内隐私政策和实际构建结果为准。

## English

Touchless is an experimental Android gesture control app. It uses the front camera and an on-device MediaPipe hand landmark model. Moving a hand upward triggers one screen swipe. Optional controls include a closed-fist Home action and an index-finger cursor with a bend-to-click gesture. A floating control lets users pause or resume detection; recognition settings, themes, and Chinese/English UI are available in the app.

**Status:** Source version 0.8.0. Broad testing on physical devices has not been completed, and this repository does not provide a validated installer. Accuracy, battery use, and background behavior vary by device and system settings.

### Build

1. Open the repository root in Android Studio and install the required Android SDK and JDK.
2. Sync Gradle, then run `./gradlew assembleDebug` (`./gradlew.bat assembleDebug` on Windows).
3. The first build needs an internet connection to download dependencies and an approximately 8 MB hand model. The model is excluded from Git; recognition runs locally on the installed device.
4. Find the debug APK under `app/build/outputs/apk/debug/`. Before distributing a release, sign it and verify permissions, behavior, and installation on target devices.

### Permissions and privacy

The app uses the camera for local hand detection, an overlay for its floating control, an accessibility service for user-triggered actions, and a foreground service notification. The manifest removes network permissions added by transitive dependencies; check the final APK before release. Camera frames are not saved or uploaded. See the in-app privacy policy and the built APK for final details.
