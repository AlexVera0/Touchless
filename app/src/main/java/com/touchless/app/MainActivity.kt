package com.touchless.app

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val displayPrefs by lazy { DisplayPreferences(this) }
    private val consentPrefs by lazy { ConsentPreferences(this) }
    private val gestureSettings by lazy { GestureSettingsRepository(this) }
    private val dark get() = displayPrefs.darkMode
    private val english get() = displayPrefs.english
    private val ink get() = if (dark) Color.rgb(236, 242, 251) else Color.rgb(23, 34, 50)
    private val muted get() = if (dark) Color.rgb(161, 177, 198) else Color.rgb(116, 128, 143)
    private val blue get() = when (displayPrefs.themeColor) {
        "mint" -> Color.rgb(0, 139, 119)
        "violet" -> Color.rgb(116, 79, 213)
        "coral" -> Color.rgb(199, 76, 72)
        "amber" -> Color.rgb(174, 109, 15)
        "rose" -> Color.rgb(190, 68, 123)
        "teal" -> Color.rgb(0, 132, 154)
        "indigo" -> Color.rgb(71, 84, 190)
        else -> Color.rgb(45, 107, 246)
    }
    private val pageColor get() = if (dark) Color.rgb(12, 19, 31) else Color.rgb(247, 249, 253)
    private val lineColor get() = if (dark) Color.rgb(57, 72, 94) else Color.rgb(232, 237, 244)

    private lateinit var homePage: ScrollView
    private lateinit var settingsPage: ScrollView
    private lateinit var homeTab: TextView
    private lateinit var settingsTab: TextView
    private lateinit var navGlass: View
    private lateinit var statusView: TextView
    private lateinit var startButton: TextView
    private lateinit var cameraCheck: TextView
    private lateinit var overlayCheck: TextView
    private lateinit var accessCheck: TextView
    private lateinit var permissionSummary: TextView
    private lateinit var detailView: TextView
    private lateinit var diagnosticView: TextView
    private var consentDialog: AlertDialog? = null

    private fun tr(zh: String, en: String) = if (english) en else zh

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageColor
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= 35) Color.TRANSPARENT else pageColor
        window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(pageColor, if (dark) Color.rgb(20, 37, 65) else Color.rgb(234, 242, 255), pageColor))
        }
        setContentView(root)
        val pages = FrameLayout(this)
        root.addView(pages, FrameLayout.LayoutParams(-1, -1))
        homePage = page()
        settingsPage = page()
        pages.addView(homePage)
        pages.addView(settingsPage)
        buildHome()
        buildSettings()
        navGlass = bottomNavigation()
        root.addView(navGlass, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = dp(18)
            rightMargin = dp(18)
            bottomMargin = dp(12)
        })
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                pages.setPadding(0, bars.top, 0, 0)
                (navGlass.layoutParams as FrameLayout.LayoutParams).apply {
                    bottomMargin = bars.bottom + dp(12)
                    navGlass.layoutParams = this
                }
                insets
            }
            root.requestApplyInsets()
        }
        selectTab(displayPrefs.selectedTab)
        if (consentPrefs.acceptedPolicyRevision != ConsentPreferences.CURRENT_POLICY_REVISION)
            showPrivacyGate()
    }

    private fun page() = ScrollView(this).apply {
        isFillViewport = true
        clipToPadding = false
        setPadding(0, 0, 0, dp(86))
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun pageBody(scroll: ScrollView) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(20), dp(18), dp(24))
        scroll.addView(this)
    }

    private fun buildHome() {
        val body = pageBody(homePage)
        body.addView(label("TOUCHLESS", 24f, ink, true))
        body.addView(label(tr("手部向上移动切换下一条", "Move your hand up to swipe"), 14f, muted)
            .apply { setPadding(0, dp(4), 0, dp(16)) })

        val hero = card()
        hero.addView(label(tr("当前状态", "Status"), 13f, muted))
        val stateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusView = label("● ${tr("已关闭", "Off")}", 22f, ink, true)
        stateRow.addView(statusView)
        diagnosticView = label(tr("未开始识别", "Recognition has not started"), 12f, muted)
            .apply { setPadding(dp(10), 0, 0, 0) }
        stateRow.addView(diagnosticView, LinearLayout.LayoutParams(0, -2, 1f))
        hero.addView(stateRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        detailView = label("", 13f, muted).apply { visibility = View.GONE }
        hero.addView(detailView)
        startButton = label(tr("开始手势控制", "Start gesture control"), 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = glassBackground(17, true)
            elevation = dp(4).toFloat()
            setOnClickListener {
                if (TouchlessService.running) stopService(Intent(this@MainActivity, TouchlessService::class.java))
                else startControl()
            }
        }
        addPressFeedback(startButton)
        hero.addView(startButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        body.addView(hero)

        val granted = grantedPermissionCount()
        val permissionSection = section(body, tr("权限设置", "Permissions"),
            tr("$granted/3 已授权", "$granted/3 granted"), granted < 3)
        val permissions = permissionSection.first
        permissionSummary = permissionSection.second
        cameraCheck = row(permissions, tr("相机权限", "Camera"),
            tr("用于识别手势", "Detects gestures")) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
        }
        separator(permissions)
        overlayCheck = row(permissions, tr("悬浮窗权限", "Overlay"),
            tr("跨应用显示控制球", "Shows the control ball over other apps")) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        separator(permissions)
        accessCheck = row(permissions, tr("无障碍服务", "Accessibility"),
            tr("发送屏幕滑动和返回桌面操作", "Performs swipes and Home actions")) {
            withAccessibilityDisclosure {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val guide = section(body, tr("控制方式", "How to use"), tr("动作说明", "Gesture guide"), false).first
        guide.addView(label(tr("✋  手部向上移动", "✋  Move your hand up"), 16f, ink, true))
        guide.addView(label(tr("屏幕上滑一次切换下一条；下移或横向挥手不执行。", "Swipes the screen up once; moving down or sideways does nothing."), 14f, muted)
            .apply { setPadding(0, dp(4), 0, dp(13)) })
        separator(guide)
        guide.addView(label(tr("✊  握拳停留约 1 秒", "✊  Hold a fist for about 1 second"), 16f, ink, true))
        val homeSwitch = Switch(this).apply {
            text = tr("握拳返回桌面", "Fist gesture goes Home")
            textSize = 16f
            setTextColor(ink)
            styleSwitch(this)
            isChecked = gestureSettings.load().homeGestureEnabled
            setOnCheckedChangeListener { _, enabled ->
                gestureSettings.save(gestureSettings.load().copy(homeGestureEnabled = enabled))
            }
        }
        guide.addView(homeSwitch, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        guide.addView(label(tr("默认关闭；开启后握拳返回桌面，原应用仍在后台。", "Off by default. When enabled, a fist goes Home and leaves the previous app open."), 14f, muted)
            .apply { setPadding(0, dp(4), 0, dp(13)) })
        separator(guide)
        guide.addView(label(tr("☝  单指光标与点击", "☝  Index finger cursor and click"), 16f, ink, true))
        val pointerSwitch = Switch(this).apply {
            text = tr("启用单指光标", "Enable index finger cursor")
            textSize = 16f; setTextColor(ink); styleSwitch(this)
            isChecked = gestureSettings.load().pointerEnabled
            setOnCheckedChangeListener { _, enabled ->
                gestureSettings.save(gestureSettings.load().copy(pointerEnabled = enabled))
            }
        }
        guide.addView(pointerSwitch, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        guide.addView(label(tr("伸出食指、收起其余三指，移动食指控制光标；轻弯食指并伸直后可再次点击。该姿态下不会触发上滑或握拳。",
            "Extend the index finger and fold the other three fingers. Move the finger to steer the cursor; bend it slightly to click, then straighten it before the next click. This pose does not trigger swipe or Home."), 14f, muted)
            .apply { setPadding(0, dp(4), 0, dp(13)) })
        guide.addView(label(tr("轻点悬浮球暂停，长按打开菜单。", "Tap the floating ball to pause; hold it for the menu."), 14f, muted))
    }

    private fun buildSettings() {
        val body = pageBody(settingsPage)
        body.addView(label(tr("设置", "Settings"), 27f, ink, true))
        body.addView(label(tr("外观、语言与手势参数", "Appearance, language and gesture controls"), 14f, muted)
            .apply { setPadding(0, dp(3), 0, dp(16)) })

        val appearance = card()
        appearance.addView(label(tr("外观与语言", "Appearance & language"), 17f, ink, true))
        val darkSwitch = Switch(this).apply {
            text = tr("夜间模式", "Dark mode")
            textSize = 16f
            setTextColor(ink)
            styleSwitch(this)
            isChecked = dark
            setOnCheckedChangeListener { _, enabled ->
                displayPrefs.darkMode = enabled
                recreate()
            }
        }
        appearance.addView(darkSwitch, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
        separator(appearance)
        val englishSwitch = Switch(this).apply {
            text = tr("英语界面 / English", "English interface / 中文")
            textSize = 16f
            setTextColor(ink)
            styleSwitch(this)
            isChecked = english
            setOnCheckedChangeListener { _, enabled ->
                displayPrefs.english = enabled
                recreate()
            }
        }
        appearance.addView(englishSwitch)
        separator(appearance)
        val themeHeader = label(tr("主题色  ·  点击展开 / 收起", "Theme color  ·  Tap to expand / collapse"), 15f, ink, true)
        appearance.addView(themeHeader)
        val themes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val choices = listOf(
            Triple("blue", tr("蓝色", "Blue"), Color.rgb(45, 107, 246)),
            Triple("mint", tr("薄荷", "Mint"), Color.rgb(0, 139, 119)),
            Triple("violet", tr("紫色", "Violet"), Color.rgb(116, 79, 213)),
            Triple("coral", tr("珊瑚", "Coral"), Color.rgb(199, 76, 72)),
            Triple("amber", tr("琥珀", "Amber"), Color.rgb(174, 109, 15)),
            Triple("rose", tr("玫红", "Rose"), Color.rgb(190, 68, 123)),
            Triple("teal", tr("青色", "Teal"), Color.rgb(0, 132, 154)),
            Triple("indigo", tr("靛蓝", "Indigo"), Color.rgb(71, 84, 190))
        )
        choices.chunked(4).forEach { group ->
            val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { (key, name, color) -> themeRow.addView(label("● $name", 13f, color, true).apply {
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                background = if (displayPrefs.themeColor == key) glassBackground(18) else null
                contentDescription = name + if (displayPrefs.themeColor == key) tr("，已选中", ", selected") else ""
                setOnClickListener { displayPrefs.themeColor = key; recreate() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f)) }
            themes.addView(themeRow)
        }
        themes.visibility = View.GONE
        themeHeader.setOnClickListener {
            themes.visibility = if (themes.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        appearance.addView(themes, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        body.addView(appearance)

        val settingsCard = section(body, tr("通用识别调节", "General recognition settings"),
            tr("适用于所有手势", "Applies to all gestures"), false).first
        settingsCard.addView(label(tr("以下参数同时作用于手部上移与握拳识别。", "These controls apply to upward hand movement and fist detection."), 13f, muted))
        addSlider(settingsCard, tr("移动距离 / 稳定范围", "Distance / stability"), 2, 20,
            gestureSettings.load().distancePercent, tr("% · 上滑距离与握拳稳定范围", "% · Swipe distance and fist stability")) {
            gestureSettings.save(gestureSettings.load().copy(distancePercent = it))
        }
        addSlider(settingsCard, tr("识别时间", "Recognition time"), 60, 400,
            gestureSettings.load().recognitionTimeMs, tr(" 毫秒 · 同时微调握拳保持时间", " ms · Also adjusts fist hold time")) {
            gestureSettings.save(gestureSettings.load().copy(recognitionTimeMs = it))
        }
        addSlider(settingsCard, tr("灵敏度", "Sensitivity"), 0, 100,
            gestureSettings.load().sensitivity, tr("% · 越高越敏感", "% · Higher is more sensitive")) {
            gestureSettings.save(gestureSettings.load().copy(sensitivity = it))
        }
        addSlider(settingsCard, tr("动作最短间隔", "Minimum interval"), 1000, 2000,
            gestureSettings.load().intervalMs, tr(" 毫秒", " ms"), 100) {
            gestureSettings.save(gestureSettings.load().copy(intervalMs = it))
        }
        settingsCard.addView(label(tr("调节后约 0.25 秒生效。误触时可调高距离或降低灵敏度；握拳仍需停留约 1 秒。",
            "Changes take effect in about 0.25 seconds. Increase distance or lower sensitivity to reduce false triggers. A fist still needs a hold of about 1 second."), 13f, muted))
        settingsCard.addView(label(tr("推荐值：距离 4% · 时间 100 毫秒 · 灵敏度 75% · 间隔 1500 毫秒",
            "Recommended: distance 4% · time 100 ms · sensitivity 75% · interval 1500 ms"), 12f, muted)
            .apply { setPadding(0, dp(10), 0, 0) })
        settingsCard.addView(actionButton(tr("恢复默认推荐参数", "Restore recommended settings"), false) {
            val current = gestureSettings.load()
            gestureSettings.save(GestureSettings().copy(homeGestureEnabled = current.homeGestureEnabled,
                pointerEnabled = current.pointerEnabled))
            recreate()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })

        val sharing = card()
        sharing.addView(label(tr("离线分享", "Offline sharing"), 17f, ink, true))
        sharing.addView(label(tr("分享当前手机安装的 APK。可在系统分享菜单中选蓝牙等本地方式。接收方仍需自行安装并授予权限。",
            "Share the APK installed on this phone using Bluetooth or another local option. The recipient must install it and grant permissions."), 13f, muted)
            .apply { setPadding(0, dp(5), 0, dp(12)) })
        val shareButton = actionButton(tr("分享 APP 安装包", "Share app APK"), false) { shareApk() }
        sharing.addView(shareButton)
        body.addView(sharing, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        val about = card().apply {
            addView(label(tr("关于  ›", "About  ›"), 17f, ink, true))
            addView(label(tr("帮助、政策、反馈与版本信息", "Help, policies, feedback and version"), 13f, muted))
            setOnClickListener { showAbout() }
        }
        body.addView(about, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        val reset = card()
        reset.addView(label(tr("重置应用", "Reset app"), 17f, ink, true))
        reset.addView(label(tr("恢复外观、语言、手势、悬浮球位置及隐私同意记录的默认值；系统授予的权限需在系统设置中管理。",
            "Restore appearance, language, gesture, floating ball position and consent settings. Manage system permissions in Android settings."), 13f, muted)
            .apply { setPadding(0, dp(5), 0, dp(12)) })
        reset.addView(actionButton(tr("重置应用设置", "Reset app settings"), false) { confirmReset() })
        body.addView(reset, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun bottomNavigation(): View {
        val glass = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = glassBackground(32)
            elevation = dp(16).toFloat()
        }
        homeTab = navItem("⌂  ${tr("首页", "Home")}") { selectTab("home") }
        settingsTab = navItem("⚙  ${tr("设置", "Settings")}") { selectTab("settings") }
        glass.addView(homeTab, LinearLayout.LayoutParams(0, dp(48), 1f))
        glass.addView(settingsTab, LinearLayout.LayoutParams(0, dp(48), 1f))
        return glass
    }

    private fun navItem(title: String, click: () -> Unit) = label(title, 15f, ink, true).apply {
        gravity = Gravity.CENTER
        setOnClickListener {
            if (ValueAnimator.areAnimatorsEnabled()) {
                animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(170).start()
                }.start()
            }
            click()
        }
    }

    private fun selectTab(tab: String) {
        val settingsSelected = tab == "settings"
        displayPrefs.selectedTab = if (settingsSelected) "settings" else "home"
        val next = if (settingsSelected) settingsPage else homePage
        val previous = if (settingsSelected) homePage else settingsPage
        val switching = next.visibility != View.VISIBLE && previous.visibility == View.VISIBLE
        previous.visibility = View.GONE
        next.visibility = View.VISIBLE
        if (switching && ValueAnimator.areAnimatorsEnabled()) {
            next.alpha = 0f
            next.translationY = dp(6).toFloat()
            next.animate().alpha(1f).translationY(0f).setDuration(220).start()
        }
        setTabAppearance(homeTab, !settingsSelected)
        setTabAppearance(settingsTab, settingsSelected)
    }

    private fun setTabAppearance(view: TextView, selected: Boolean) {
        view.setTextColor(if (selected) Color.WHITE else ink)
        view.background = if (selected) glassBackground(25, true) else null
        view.contentDescription = view.text.toString() + if (selected) tr("，已选中", ", selected") else ""
    }

    private fun actionButton(title: String, prominent: Boolean, click: () -> Unit) =
        label(title, 15f, if (prominent) Color.WHITE else if (dark) Color.WHITE else blue, true).apply {
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = glassBackground(16, prominent)
            setOnClickListener { click() }
            addPressFeedback(this)
        }

    private fun styleSwitch(control: Switch) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        control.thumbTintList = android.content.res.ColorStateList(states,
            intArrayOf(Color.WHITE, Color.WHITE))
        control.trackTintList = android.content.res.ColorStateList(states,
            intArrayOf(blue, if (dark) Color.rgb(89, 103, 124) else Color.rgb(196, 204, 217)))
        control.showText = false
        control.minimumHeight = dp(48)
    }

    private fun addPressFeedback(view: View) {
        view.setOnTouchListener { touched, event ->
            if (ValueAnimator.areAnimatorsEnabled()) when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> touched.animate().scaleX(.98f).scaleY(.98f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    touched.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
            false
        }
    }

    private fun dialogBuilder() = AlertDialog.Builder(this,
        if (dark) android.R.style.Theme_Material_Dialog_Alert
        else android.R.style.Theme_Material_Light_Dialog_Alert)

    private fun link(title: String, click: () -> Unit) = label(title, 15f, blue, true).apply {
        setPadding(0, dp(11), 0, dp(5))
        minimumHeight = dp(48)
        setOnClickListener { click() }
    }

    private fun showPrivacyGate() {
        if (consentDialog?.isShowing == true) return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        content.addView(label(tr(
            "请先阅读并同意《用户协议》和《隐私政策》。本应用在你授权后使用前置摄像头识别手势；无障碍服务仅执行手势对应的滑动或返回桌面操作。画面不保存、不上传。",
            "Please read and accept the User Agreement and Privacy Policy. With your permission, the app uses the front camera for gesture recognition. Accessibility only performs gesture-triggered swipe or Home actions. Camera frames are not saved or uploaded."
        ), 14f, ink))
        content.addView(link(tr("查看《用户协议》", "Read User Agreement")) {
            showDocument(tr("用户协议", "User Agreement"), PolicyContent.agreement(english))
        })
        content.addView(link(tr("查看《隐私政策》", "Read Privacy Policy")) {
            showDocument(tr("隐私政策", "Privacy Policy"), PolicyContent.privacy(english))
        })
        consentDialog = dialogBuilder()
            .setTitle(tr("隐私授权", "Privacy consent"))
            .setView(content)
            .setPositiveButton(tr("同意并继续", "Agree and continue")) { _, _ ->
                consentPrefs.acceptedPolicyRevision = ConsentPreferences.CURRENT_POLICY_REVISION
                consentDialog = null
            }
            .setNegativeButton(tr("不同意并退出", "Decline and exit")) { _, _ ->
                consentDialog = null
                stopService(Intent(this, TouchlessService::class.java))
                finishAffinity()
            }
            .setCancelable(false)
            .create()
        consentDialog?.show()
    }

    private fun showDocument(title: String, body: String) {
        val scroll = ScrollView(this)
        scroll.addView(label(body, 14f, ink).apply {
            setPadding(dp(22), dp(16), dp(22), dp(18))
            setTextIsSelectable(true)
        })
        dialogBuilder().setTitle(title).setView(scroll)
            .setPositiveButton(tr("关闭", "Close"), null).show()
    }

    private fun showHelp() = showDocument(tr("使用帮助", "Help"), tr(
        "1. 在首页授予相机、悬浮窗和无障碍权限。\n\n2. 点“开始手势控制”，保持手机稳定，让手部向上移动即可上滑一次。\n\n3. 如需握拳返回桌面，请在首页“控制方式”中开启开关，四指握紧并保持约 1 秒。\n\n4. 开启单指光标后，伸出食指并收起其余三指；移动食指控制光标，轻弯食指点击。再次伸直后才能再次点击。\n\n5. 轻点悬浮球可暂停；长按可打开菜单。",
        "1. Grant camera, overlay and accessibility permissions on Home.\n\n2. Start control, keep the phone steady and move your hand up for one swipe.\n\n3. Enable the fist switch under How to use, then hold a tight fist for about 1 second to go Home.\n\n4. Enable the index cursor, extend the index finger and fold the other three. Move to steer; bend slightly to click. Straighten before clicking again.\n\n5. Tap the floating ball to pause; hold it for the menu."
    ))

    private fun showAbout() {
        val items = arrayOf(tr("使用帮助", "Help"), tr("隐私政策", "Privacy Policy"),
            tr("用户协议", "User Agreement"), tr("版本信息", "Version"))
        dialogBuilder().setTitle(tr("关于", "About")).setItems(items) { _, which ->
            when (which) {
                0 -> showHelp()
                1 -> showDocument(items[1], PolicyContent.privacy(english))
                2 -> showDocument(items[2], PolicyContent.agreement(english))
                3 -> showDocument(items[3], "Touchless ${BuildConfig.VERSION_NAME}  ·  ${tr("构建号", "Build")} ${BuildConfig.VERSION_CODE}")
            }
        }.setNegativeButton(tr("关闭", "Close"), null).show()
    }

    private fun confirmReset() {
        dialogBuilder().setTitle(tr("确认重置应用？", "Reset app settings?"))
            .setMessage(tr("将停止手势控制，恢复外观、语言、手势参数、悬浮球位置及隐私同意记录的默认值，并清除已缓存的分享 APK。随后会重新显示隐私授权。Android 系统权限不会自动撤销。",
                "Gesture control will stop. Appearance, language, gesture settings, floating ball position and consent records will return to defaults, and cached share APKs will be removed. Privacy consent will appear again. Android permissions are managed separately."))
            .setPositiveButton(tr("确认重置", "Reset")) { _, _ ->
                stopService(Intent(this, TouchlessService::class.java))
                displayPrefs.reset()
                gestureSettings.reset()
                consentPrefs.reset()
                getSharedPreferences("ui", MODE_PRIVATE).edit().clear().commit()
                File(cacheDir, "share").listFiles()?.forEach { it.delete() }
                recreate()
            }.setNegativeButton(tr("取消", "Cancel"), null).show()
    }

    private fun withAccessibilityDisclosure(onAccepted: () -> Unit) {
        if (consentPrefs.acceptedPolicyRevision != ConsentPreferences.CURRENT_POLICY_REVISION) {
            showPrivacyGate(); return
        }
        if (consentPrefs.accessibilityDisclosureAccepted) { onAccepted(); return }
        dialogBuilder()
            .setTitle(tr("无障碍服务用途", "Accessibility service use"))
            .setMessage(tr(
                "Touchless 的无障碍服务只在识别到你启用的手势时执行屏幕上滑或返回桌面。服务不读取屏幕窗口内容，不保存或上传屏幕信息。是否同意此用途并继续前往系统设置？",
                "Touchless uses accessibility only to swipe up or go Home when it recognizes a gesture you enabled. It does not read window content or save or upload screen information. Agree and continue to Android settings?"
            ))
            .setPositiveButton(tr("同意并继续", "Agree and continue")) { _, _ ->
                consentPrefs.accessibilityDisclosureAccepted = true
                onAccepted()
            }
            .setNegativeButton(tr("暂不启用", "Not now"), null)
            .show()
    }

    private fun startControl() {
        if (consentPrefs.acceptedPolicyRevision != ConsentPreferences.CURRENT_POLICY_REVISION) {
            showPrivacyGate(); return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101); return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        if (!consentPrefs.accessibilityDisclosureAccepted) {
            withAccessibilityDisclosure { startControl() }
            return
        }
        if (!accessibilityEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return
        }
        if (TouchlessAccessibilityService.instance == null) {
            detailView.text = tr("无障碍已开启，等待系统连接服务。请稍后重试。",
                "Accessibility is enabled; waiting for the system to connect. Try again shortly.")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102)
        try { startForegroundService(Intent(this, TouchlessService::class.java)) }
        catch (e: Exception) {
            detailView.text = tr("启动失败：", "Could not start: ") + (e.message ?: tr("请检查系统设置", "Check system settings"))
        }
    }

    private fun accessibilityEnabled(): Boolean {
        if (TouchlessAccessibilityService.instance != null) return true
        val component = ComponentName(this, TouchlessAccessibilityService::class.java)
        val saved = Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        if (saved.split(':').any { ComponentName.unflattenFromString(it) == component }) return true
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == component }
    }

    private fun grantedPermissionCount() = listOf(
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        Settings.canDrawOverlays(this), accessibilityEnabled()
    ).count { it }

    private fun shareApk() {
        if (!applicationInfo.splitSourceDirs.isNullOrEmpty()) {
            Toast.makeText(this, tr("当前安装为拆分 APK，无法作为单个安装包分享。",
                "This installation uses split APKs and cannot be shared as one installer."), Toast.LENGTH_LONG).show()
            return
        }
        val source = File(applicationInfo.sourceDir)
        if (!source.isFile) {
            Toast.makeText(this, tr("找不到已安装的 APK。", "Installed APK was not found."), Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, tr("正在准备安装包…", "Preparing APK…"), Toast.LENGTH_SHORT).show()
        Thread({
            try {
                val folder = File(cacheDir, "share").apply { mkdirs() }
                val apk = File(folder, "Touchless-${BuildConfig.VERSION_NAME}.apk")
                source.copyTo(apk, overwrite = true)
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(contentResolver, "Touchless APK", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    try { startActivity(Intent.createChooser(send, tr("分享 Touchless 安装包", "Share Touchless APK"))) }
                    catch (e: Exception) { shareError(e) }
                }
            } catch (e: Exception) { runOnUiThread { shareError(e) } }
        }, "apk-share").start()
    }

    private fun shareError(error: Exception) {
        Toast.makeText(this, tr("无法分享安装包：", "Could not share APK: ") +
            (error.message ?: tr("未知错误", "Unknown error")), Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        handler.post(object : Runnable {
            override fun run() {
                cameraCheck.text = if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    tr("已授权", "Granted") else tr("去授权", "Grant")
                overlayCheck.text = if (Settings.canDrawOverlays(this@MainActivity)) tr("已授权", "Granted") else tr("去授权", "Grant")
                accessCheck.text = when {
                    TouchlessAccessibilityService.instance != null -> tr("已连接", "Connected")
                    accessibilityEnabled() -> tr("连接中", "Connecting")
                    else -> tr("去开启", "Enable")
                }
                val granted = grantedPermissionCount()
                permissionSummary.text = tr("$granted/3 已授权", "$granted/3 granted")
                statusView.text = when {
                    !TouchlessService.running -> "● ${tr("已关闭", "Off")}"
                    TouchlessService.paused -> "● ${tr("已暂停", "Paused")}"
                    else -> "● ${tr("正在识别", "Recognizing")}"
                }
                statusView.setTextColor(if (TouchlessService.running && !TouchlessService.paused)
                    Color.rgb(33, 171, 100) else ink)
                startButton.text = if (TouchlessService.running) tr("退出手势控制", "Stop gesture control")
                    else tr("开始手势控制", "Start gesture control")
                detailView.text = when {
                    TouchlessService.running -> statusText(TouchlessService.status)
                    accessibilityEnabled() && TouchlessAccessibilityService.instance == null ->
                        tr("无障碍已开启，等待系统连接服务。", "Accessibility is enabled; waiting for connection.")
                    else -> ""
                }
                detailView.visibility = if (detailView.text.isEmpty()) View.GONE else View.VISIBLE
                diagnosticView.text = when {
                    !TouchlessService.running || TouchlessService.paused -> tr("未开始识别", "Recognition has not started")
                    TouchlessService.handDetected -> tr(
                        "已检测到手 · 移动幅度 ${TouchlessService.movementPercent}% · 距离设置 ${gestureSettings.load().distancePercent}%",
                        "Hand detected · Movement ${TouchlessService.movementPercent}% · Distance ${gestureSettings.load().distancePercent}%")
                    else -> tr("未检测到手 · 请让前置摄像头看到手", "No hand detected · Show your hand to the front camera")
                }
                handler.postDelayed(this, 500)
            }
        })
    }

    private fun statusText(raw: String): String {
        if (!english) return raw
        return when (raw) {
            "已关闭" -> "Off"
            "正在准备" -> "Preparing"
            "正在加载手部模型" -> "Loading hand model"
            "正在识别" -> "Recognizing"
            "已暂停" -> "Paused"
            "屏幕已锁定，请手动恢复" -> "Screen locked; resume manually"
            "未检测到手" -> "No hand detected"
            "手部短暂离开画面" -> "Hand briefly left the frame"
            "等待下次动作" -> "Waiting for the next gesture"
            "张开手后可继续操作" -> "Open your hand before the next gesture"
            "请让手停稳后再做下一次" -> "Hold your hand still before the next gesture"
            "已识别握拳" -> "Fist recognized"
            "手机移动中，已暂停手势识别" -> "Phone moving; gesture detection paused"
            "单指光标已启用" -> "Index cursor active"
            "单指点击失败，请检查无障碍权限" -> "Click failed; check accessibility permission"
            "正在观察手部移动" -> "Watching hand movement"
            "正在确认动作" -> "Confirming gesture"
            "手部向下不执行操作" -> "Moving down does not trigger an action"
            "横向移动不执行操作" -> "Moving sideways does not trigger an action"
            "正在确认握拳" -> "Confirming fist"
            "移动距离不足" -> "Move farther"
            "移动速度过慢" -> "Move faster"
            "移动方向不稳定" -> "Movement direction is unstable"
            "已识别手部向上移动" -> "Upward movement recognized"
            "无障碍服务未连接，请检查权限设置" -> "Accessibility service disconnected; check permissions"
            "已返回桌面" -> "Returned Home"
            "返回桌面失败，请检查无障碍权限" -> "Home action failed; check accessibility"
            "已执行屏幕上滑" -> "Swiped up"
            "屏幕滑动被系统取消" -> "Swipe canceled by system"
            "系统未接受滑动，请检查无障碍权限" -> "Swipe rejected; check accessibility"
            "请开启悬浮窗权限" -> "Enable overlay permission"
            else -> when {
                raw.startsWith("模型加载失败：") -> "Model failed to load: " + raw.substringAfter('：')
                raw.startsWith("相机不可用：") -> "Camera unavailable: " + raw.substringAfter('：')
                raw.startsWith("识别异常：") -> "Recognition error: " + raw.substringAfter('：')
                raw.startsWith("悬浮球显示失败：") -> "Could not show overlay: " + raw.substringAfter('：')
                else -> raw
            }
        }
    }

    override fun onPause() { handler.removeCallbacksAndMessages(null); super.onPause() }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private fun glassBackground(radius: Int, accent: Boolean = false) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        if (accent) intArrayOf(mixColor(blue, Color.WHITE, .28f), blue, mixColor(blue, Color.BLACK, .24f))
        else if (dark) intArrayOf(
            Color.argb(235, 83, 111, 146), Color.argb(225, 49, 70, 103), Color.argb(225, 31, 47, 73))
        else intArrayOf(
            Color.argb(245, 255, 255, 255), Color.argb(226, 243, 249, 255), Color.argb(211, 215, 231, 252))
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), if (accent) Color.argb(190, 200, 228, 255)
            else if (dark) Color.argb(150, 198, 220, 247) else Color.WHITE)
    }
    private fun mixColor(a: Int, b: Int, amount: Float) = Color.rgb(
        (Color.red(a) * (1f - amount) + Color.red(b) * amount).toInt(),
        (Color.green(a) * (1f - amount) + Color.green(b) * amount).toInt(),
        (Color.blue(a) * (1f - amount) + Color.blue(b) * amount).toInt()
    )
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (dark) intArrayOf(Color.argb(235, 42, 58, 82), Color.argb(215, 26, 39, 61))
            else intArrayOf(Color.argb(245, 255, 255, 255), Color.argb(227, 247, 251, 255))).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), if (dark) Color.argb(105, 170, 198, 234) else Color.WHITE)
        }
        elevation = dp(3).toFloat()
    }
    private fun separator(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(lineColor) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(14) })
    }
    private fun section(body: LinearLayout, title: String, summary: String, expanded: Boolean): Pair<LinearLayout, TextView> {
        val container = card()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
        }
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(label(title, 17f, ink, true))
        val summaryView = label(summary, 12f, muted)
        names.addView(summaryView)
        header.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = label(if (expanded) tr("收起", "Collapse") else tr("展开", "Expand"), 13f, blue, true).apply {
            gravity = Gravity.CENTER
            minimumWidth = dp(56)
            minimumHeight = dp(36)
            background = glassBackground(18)
        }
        header.addView(toggle)
        container.addView(header)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (expanded) View.VISIBLE else View.GONE
        }
        container.addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        header.setOnClickListener {
            val open = content.visibility != View.VISIBLE
            content.visibility = if (open) View.VISIBLE else View.GONE
            toggle.text = if (open) tr("收起", "Collapse") else tr("展开", "Expand")
            header.contentDescription = title + (if (english) ", " else "，") + toggle.text
        }
        body.addView(container, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        return content to summaryView
    }
    private fun row(parent: LinearLayout, title: String, subtitle: String, click: () -> Unit): TextView {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setOnClickListener { click() }
        }
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(label(title, 16f, ink, true))
        names.addView(label(subtitle, 12f, muted).apply { setPadding(0, dp(4), 0, 0) })
        line.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
        val action = label(tr("去授权", "Grant"), 13f, blue, true)
        line.addView(action)
        parent.addView(line)
        return action
    }
    private fun addSlider(parent: LinearLayout, title: String, min: Int, max: Int, initial: Int,
                          suffix: String, step: Int = 1, onValue: (Int) -> Unit) {
        val valueLabel = label("$title：$initial$suffix", 15f, ink, true)
        parent.addView(valueLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val slider = SeekBar(this).apply {
            this.max = (max - min) / step
            progress = (initial - min) / step
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            thumbTintList = android.content.res.ColorStateList.valueOf(blue)
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val value = min + progress * step
                valueLabel.text = "$title：$value$suffix"
                onValue(value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        parent.addView(slider, LinearLayout.LayoutParams(-1, dp(48)))
    }
}
