package com.touchless.app

object PolicyContent {
    fun privacy(english: Boolean) = if (english) """
        Privacy Policy — Touchless

        1. What the app uses
        With your permission, the app uses the front camera to detect hand landmarks on this device. Camera frames are processed in memory for gesture recognition. The app does not save photos or videos or upload camera frames. The installed app does not request Internet access.

        2. Accessibility and overlay
        If you enable the accessibility service, it sends the screen swipe or Home action you trigger with a recognized gesture. The service is configured not to retrieve window content and does not process accessibility events. Overlay permission shows the floating control ball over other apps. You can turn these permissions off in Android settings.

        3. Data stored on your device
        Gesture settings, language, dark mode, theme color, floating ball position, and your consent choices are stored locally in app preferences. Sharing the app copies its installed APK to the app cache so an app you select from Android's share menu can receive it. The selected sharing app controls any further transfer. Clear app data or uninstall Touchless to remove local preferences and cached files.

        4. Other permissions
        Notification permission allows the foreground service notification to appear in the notification drawer. The app does not require an account, advertising ID, contacts, location, or microphone.

        5. Your choices
        You may decline this notice and leave the app. After accepting, you can review these documents in Settings, stop gesture control, revoke system permissions, clear app data, or uninstall. Android may keep a system permission enabled until you turn it off in system settings.
    """.trimIndent() else """
        隐私政策 — Touchless

        1. 应用使用的信息
        经你授权后，应用使用前置摄像头，在本机识别手部关键点。画面只在内存中用于手势识别；应用不保存照片或视频，也不上传摄像头画面。安装后的应用不申请互联网访问权限。

        2. 无障碍与悬浮窗
        开启无障碍服务后，应用仅根据识别出的手势发送屏幕滑动或返回桌面操作。服务配置为不读取窗口内容，也不处理无障碍事件。悬浮窗权限用于在其他应用上方显示控制球。你可随时在 Android 系统设置中关闭这些权限。

        3. 本机保存的数据
        手势参数、语言、夜间模式、主题色、悬浮球位置和同意记录保存在本机应用偏好设置中。分享 APP 时，应用会将当前安装的 APK 复制到应用缓存，再交给你在系统分享菜单中选择的应用；后续传输由该分享应用处理。清除应用数据或卸载 Touchless 可删除本机偏好和缓存文件。

        4. 其他权限
        通知权限用于在通知栏显示前台服务运行提示。应用无需账号，也不申请广告标识、通讯录、位置或麦克风权限。

        5. 你的选择
        你可以不同意并退出。接受后可在设置中再次查看本文，停止手势控制，在系统设置撤销权限、清除应用数据或卸载。系统权限可能在退出应用后继续保持开启，需由你在系统设置中关闭。
    """.trimIndent()

    fun agreement(english: Boolean) = if (english) """
        User Agreement — Touchless

        1. Touchless turns your deliberate hand movements into screen actions. Use it only on devices and in apps where you are allowed to do so. Check the screen result before relying on an action.

        2. Recognition accuracy, background operation, and accessibility availability depend on lighting, camera placement, the Android version, device settings, and other apps. You can pause or stop control using the floating ball or the app.

        3. You decide whether to grant camera, overlay, accessibility, and notification permissions. Declining this agreement exits the app. Android system permissions are managed separately in system settings.

        4. If you share the APK, verify its source and version before installation. The recipient must choose to install it and grant their own permissions. The current development build has not been validated on all devices.
    """.trimIndent() else """
        用户协议 — Touchless

        1. Touchless 将你主动做出的手部动作转换为屏幕操作。请仅在你有权使用的设备和应用中使用，并核对操作后的屏幕结果。

        2. 识别准确率、后台运行和无障碍服务可用性受光线、相机位置、Android 版本、设备设置及其他应用影响。你可通过悬浮球或应用暂停、退出控制。

        3. 相机、悬浮窗、无障碍和通知权限由你决定是否授予。不同意本协议将退出应用；Android 系统权限需在系统设置中单独管理。

        4. 分享 APK 时，请核对安装包来源和版本。接收方需自行选择安装并授予权限。当前开发版尚未在所有设备上完成验证。
    """.trimIndent()
}
