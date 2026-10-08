package com.touchless.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class TouchlessService : LifecycleService() {
    companion object {
        const val ACTION_PAUSE = "com.touchless.app.PAUSE"
        const val ACTION_RESUME = "com.touchless.app.RESUME"
        const val ACTION_STOP = "com.touchless.app.STOP"
        @Volatile var running = false
        @Volatile var paused = false
        @Volatile var status = "已关闭"
        @Volatile var handDetected = false
        @Volatile var movementPercent = 0
    }
    private val main = Handler(Looper.getMainLooper())
    private val displayPrefs by lazy { DisplayPreferences(this) }
    private fun tr(zh: String, en: String) = if (displayPrefs.english) en else zh
    private val executor = Executors.newSingleThreadExecutor()
    private val engine = GestureEngine()
    private val pointerEngine = PointerEngine()
    private var pointer: TextView? = null
    private var pointerParams: WindowManager.LayoutParams? = null
    private var sensorManager: SensorManager? = null
    private val gravityEstimate = FloatArray(3)
    private var gravityReady = false
    @Volatile private var deviceMotionUntil = 0L
    private val motionListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            var squared = 0f
            for (i in 0..2) {
                val component = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    if (!gravityReady) gravityEstimate[i] = v[i]
                    else gravityEstimate[i] = gravityEstimate[i] * .85f + v[i] * .15f
                    v[i] - gravityEstimate[i]
                } else v[i]
                squared += component * component
            }
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) gravityReady = true
            val magnitude = kotlin.math.sqrt(squared)
            val threshold = if (event.sensor.type == Sensor.TYPE_GYROSCOPE) .12f else .40f
            if (magnitude > threshold) deviceMotionUntil = SystemClock.uptimeMillis() + 500L
        }
    }
    private val gestureSettings by lazy { GestureSettingsRepository(this) }
    private var activeSettings = GestureSettings()
    private var lastSettingsRead = 0L
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: HandLandmarker? = null
    private var lastFrame = 0L
    private var lastHand = 0L
    private var packedPixels: ByteBuffer? = null
    private var rowBytes: ByteArray? = null
    private var reusableBitmap: Bitmap? = null
    private var orientedBitmap: Bitmap? = null
    private var orientedCanvas: Canvas? = null
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    @Volatile private var cameraGeneration = 0
    private var feedbackUntil = 0L
    private var statusHoldUntil = 0L
    private var windowManager: WindowManager? = null
    private var ball: TextView? = null
    private var menu: LinearLayout? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel("touchless", tr("隔空手势控制", "Air gesture control"), NotificationManager.IMPORTANCE_LOW))
        val notification = notification("正在准备前置摄像头")
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        else startForeground(1, notification)
        running = true; paused = false; status = "正在准备"
        if (!showBall()) { stopSelf(); return }
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager?.registerListener(motionListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        val translationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        translationSensor?.let { sensorManager?.registerListener(motionListener, it, SensorManager.SENSOR_DELAY_GAME) }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) pauseCamera("屏幕已锁定，请手动恢复")
            }
        }
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseCamera("已暂停")
            ACTION_RESUME -> if (paused) { paused = false; startCamera() }
            ACTION_STOP -> stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun notification(message: String): Notification {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val shownMessage = if (!displayPrefs.english) message else when (message) {
            "正在准备前置摄像头" -> "Preparing front camera"
            "前置摄像头正在识别" -> "Recognizing with front camera"
            "已暂停" -> "Paused"
            "屏幕已锁定，请手动恢复" -> "Screen locked; resume manually"
            else -> message
        }
        return Notification.Builder(this, "touchless")
            .setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("Touchless")
            .setContentText(shownMessage).setContentIntent(intent).setOngoing(true).build()
    }

    private fun startCamera() {
        if (paused || !running) return
        val generation = ++cameraGeneration
        status = "正在加载手部模型"; updateBall(Color.GRAY, true)
        executor.execute {
            if (paused || !running || generation != cameraGeneration) return@execute
            try {
                detector?.close(); detector = null
                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
                    .setRunningMode(RunningMode.VIDEO).setNumHands(1)
                    .setMinHandDetectionConfidence(.40f).setMinHandPresenceConfidence(.40f)
                    .setMinTrackingConfidence(.45f).build()
                val created = HandLandmarker.createFromOptions(this, options)
                if (paused || !running || generation != cameraGeneration) {
                    created.close(); return@execute
                }
                detector = created
                main.post { bindCamera(generation) }
            } catch (e: Exception) {
                main.post {
                    if (running && !paused && generation == cameraGeneration)
                        pauseCamera("模型加载失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    private fun bindCamera(generation: Int) {
        if (paused || !running || generation != cameraGeneration) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                if (paused || !running || generation != cameraGeneration) return@addListener
                val provider = future.get(); cameraProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                    .build()
                analysis.setAnalyzer(executor) { analyzeFrame(it) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                status = "正在识别"; updateBall(Color.rgb(47, 191, 113), true)
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification("前置摄像头正在识别"))
            } catch (e: Exception) {
                if (running && !paused && generation == cameraGeneration)
                    pauseCamera("相机不可用：${e.message ?: "请检查是否被占用"}")
            }
        }, java.util.concurrent.Executor { command -> main.post(command) })
    }

    private fun analyzeFrame(image: ImageProxy) {
        val generation = cameraGeneration
        try {
            val now = SystemClock.uptimeMillis()
            val interval = if (now - lastHand > 2500) 120 else 65
            if (now - lastFrame < interval || paused || !running) return
            lastFrame = now
            val plane = image.planes[0]
            val buffer = plane.buffer
            val requiredBytes = image.width * image.height * 4
            val pixels = packedPixels?.takeIf { it.capacity() == requiredBytes }
                ?: ByteBuffer.allocateDirect(requiredBytes).also { packedPixels = it }
            val row = rowBytes?.takeIf { it.size == image.width * 4 }
                ?: ByteArray(image.width * 4).also { rowBytes = it }
            val bitmap = reusableBitmap?.takeIf { !it.isRecycled && it.width == image.width && it.height == image.height }
                ?: Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).also {
                    reusableBitmap?.recycle(); reusableBitmap = it
                }
            buffer.rewind()
            pixels.clear()
            for (y in 0 until image.height) {
                buffer.position(y * plane.rowStride)
                buffer.get(row)
                pixels.put(row)
            }
            pixels.rewind(); bitmap.copyPixelsFromBuffer(pixels)
            val oriented = orientFrame(bitmap, image.imageInfo.rotationDegrees)
            val result = detector?.detectForVideo(BitmapImageBuilder(oriented).build(), now)
            if (paused || !running || generation != cameraGeneration) return
            val hands = result?.landmarks() ?: emptyList()
            if (hands.isNotEmpty()) lastHand = now
            if (now - lastSettingsRead >= 250L) {
                activeSettings = gestureSettings.load()
                lastSettingsRead = now
            }
            val deviceMoving = now < deviceMotionUntil
            val pointerDecision = if (activeSettings.pointerEnabled)
                pointerEngine.analyze(hands.firstOrNull(), now, deviceMoving)
                else { pointerEngine.reset(); PointerEngine.Result(false) }
            // The index pose owns the frame; it cannot trigger a swipe or Home.
            val decision = if (pointerDecision.active) {
                engine.reset()
                GestureEngine.Result(GestureEngine.State.HAND_DETECTED, "单指光标已启用")
            } else engine.analyze(hands, now, activeSettings, deviceMoving)
            handDetected = hands.isNotEmpty()
            movementPercent = decision.movementPercent
            if (now >= statusHoldUntil) status = decision.reason
            main.post {
                if (paused || !running || generation != cameraGeneration) return@post
                updatePointer(pointerDecision)
                if (pointerDecision.click) {
                    val service = TouchlessAccessibilityService.instance
                    val metrics = resources.displayMetrics
                    val x = pointerDecision.x * metrics.widthPixels
                    val y = pointerDecision.y * metrics.heightPixels
                    if (service == null || !service.click(x, y) { completed ->
                            main.post { if (completed && running && !paused) showFeedback("✓") }
                        }) {
                        status = "单指点击失败，请检查无障碍权限"
                        statusHoldUntil = SystemClock.uptimeMillis() + 1800
                    }
                }
                if (!paused) updateBall(when {
                    hands.isNotEmpty() -> Color.rgb(245, 182, 61)
                    else -> Color.rgb(47, 191, 113)
                })
                if (decision.action != null && running && !paused) {
                    val service = TouchlessAccessibilityService.instance
                    if (service == null) {
                        status = "无障碍服务未连接，请检查权限设置"
                        statusHoldUntil = SystemClock.uptimeMillis() + 1800
                    } else if (decision.action == GestureEngine.Action.HOME) {
                        if (service.goHome()) {
                            status = "已返回桌面"
                            statusHoldUntil = SystemClock.uptimeMillis() + 1400
                            showFeedback("⌂")
                        } else {
                            status = "返回桌面失败，请检查无障碍权限"
                            statusHoldUntil = SystemClock.uptimeMillis() + 1800
                        }
                    } else {
                        val accepted = service.swipe { completed ->
                            main.post {
                                if (!running || paused) return@post
                                if (completed) {
                                    status = "已执行屏幕上滑"
                                    statusHoldUntil = SystemClock.uptimeMillis() + 1400
                                    showFeedback("↑")
                                } else {
                                    status = "屏幕滑动被系统取消"
                                    statusHoldUntil = SystemClock.uptimeMillis() + 1800
                                }
                            }
                        }
                        if (!accepted) {
                            status = "系统未接受滑动，请检查无障碍权限"
                            statusHoldUntil = SystemClock.uptimeMillis() + 1800
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (running && !paused && generation == cameraGeneration)
                status = "识别异常：${e.message ?: "未知错误"}"
        } finally { image.close() }
    }

    // MediaPipe's VIDEO call is synchronous, so the output bitmap can be reused on the next frame.
    private fun orientFrame(source: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()); postScale(-1f, 1f) }
        val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        matrix.mapRect(bounds)
        matrix.postTranslate(-bounds.left, -bounds.top)
        val width = bounds.width().roundToInt().coerceAtLeast(1)
        val height = bounds.height().roundToInt().coerceAtLeast(1)
        val output = orientedBitmap?.takeIf { !it.isRecycled && it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                orientedBitmap?.recycle()
                orientedBitmap = it
                orientedCanvas = Canvas(it)
            }
        val canvas = orientedCanvas ?: Canvas(output).also { orientedCanvas = it }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(source, matrix, imagePaint)
        return output
    }

    private fun pauseCamera(message: String) {
        paused = true; status = message; handDetected = false; movementPercent = 0
        hidePointer()
        ++cameraGeneration
        cameraProvider?.unbindAll()
        executor.execute {
            engine.reset()
            pointerEngine.reset()
            detector?.close(); detector = null
            reusableBitmap?.recycle(); reusableBitmap = null
            orientedCanvas = null
            orientedBitmap?.recycle(); orientedBitmap = null
            packedPixels = null; rowBytes = null
        }
        feedbackUntil = 0L; ball?.text = "✦"; updateBall(Color.GRAY, true)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification(message))
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color); setStroke(dp(2), Color.WHITE)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun updateBall(color: Int, force: Boolean = false) {
        if (!force && SystemClock.uptimeMillis() < feedbackUntil) return
        ball?.background = circle(color)
    }
    private fun showFeedback(symbol: String) {
        feedbackUntil = SystemClock.uptimeMillis() + 800
        ball?.text = symbol
        ball?.background = circle(Color.rgb(35, 115, 240))
        main.postDelayed({
            if (SystemClock.uptimeMillis() >= feedbackUntil) {
                ball?.text = "✦"
                updateBall(if (paused) Color.GRAY else Color.rgb(47, 191, 113), true)
            }
        }, 850)
    }
    private fun layoutParams(width: Int, height: Int, flags: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) =
        WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }

    private fun updatePointer(result: PointerEngine.Result) {
        if (!result.visible) { hidePointer(); return }
        val wm = windowManager ?: return
        val size = dp(28)
        val view = pointer ?: TextView(this).apply {
            text = "●"; textSize = 24f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(35, 115, 240))
            background = circle(Color.argb(175, 255, 255, 255))
        }.also {
            val params = layoutParams(size, size, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            pointerParams = params
            try { wm.addView(it, params); pointer = it } catch (_: Exception) { return }
        }
        val params = pointerParams ?: return
        params.x = (result.x * resources.displayMetrics.widthPixels).toInt() - size / 2
        params.y = (result.y * resources.displayMetrics.heightPixels).toInt() - size / 2
        wm.updateViewLayout(view, params)
    }

    private fun hidePointer() {
        pointer?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        pointer = null; pointerParams = null
    }

    private fun showBall(): Boolean {
        if (!Settings.canDrawOverlays(this)) { status = "请开启悬浮窗权限"; return false }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager; windowManager = wm
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        val params = layoutParams(dp(36), dp(36)).apply {
            x = prefs.getInt("ball_x", 0); y = prefs.getInt("ball_y", dp(180))
        }
        ballParams = params
        val view = TextView(this).apply {
            text = "✦"; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            alpha = .66f; background = circle(Color.GRAY)
        }
        try { wm.addView(view, params) }
        catch (e: Exception) {
            status = "悬浮球显示失败：${e.message ?: "请检查悬浮窗权限"}"
            return false
        }
        ball = view
        var downX = 0f; var downY = 0f; var initialX = 0; var initialY = 0; var downTime = 0L; var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; initialX = params.x; initialY = params.y; downTime = SystemClock.uptimeMillis(); moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.rawX - downX) > dp(8) || kotlin.math.abs(event.rawY - downY) > dp(8)) moved = true
                    if (moved) { params.x = initialX + (event.rawX - downX).toInt(); params.y = initialY + (event.rawY - downY).toInt(); wm.updateViewLayout(view, params) }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val width = resources.displayMetrics.widthPixels
                        params.x = if (params.x + dp(18) < width / 2) 0 else width - dp(36)
                        wm.updateViewLayout(view, params)
                        prefs.edit().putInt("ball_x", params.x).putInt("ball_y", params.y).apply()
                    } else if (SystemClock.uptimeMillis() - downTime > 550) showMenu()
                    else if (paused) { paused = false; startCamera() } else pauseCamera("已暂停")
                    true
                }
                else -> true
            }
        }
        return true
    }

    private fun showMenu() {
        menu?.let { windowManager?.removeView(it); menu = null; return }
        val wm = windowManager ?: return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
            elevation = dp(8).toFloat()
        }
        fun item(label: String, action: () -> Unit) {
            box.addView(TextView(this).apply {
                text = label; textSize = 15f; setTextColor(Color.rgb(28, 35, 45)); setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnClickListener {
                    if (menu === box) { menu = null; wm.removeView(box) }
                    action()
                }
            })
        }
        item(if (paused) tr("继续识别", "Resume") else tr("暂停识别", "Pause")) {
            if (paused) { paused = false; startCamera() } else pauseCamera("已暂停")
        }
        item(tr("打开 Touchless", "Open Touchless")) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        item(tr("退出", "Exit")) { stopSelf() }
        val params = layoutParams(dp(160), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = if ((ballParams?.x ?: 0) < resources.displayMetrics.widthPixels / 2) dp(50) else resources.displayMetrics.widthPixels - dp(210)
            y = ballParams?.y ?: dp(180)
        }
        menu = box; wm.addView(box, params)
    }

    override fun onDestroy() {
        running = false; paused = false; status = "已关闭"; handDetected = false; movementPercent = 0
        ++cameraGeneration
        sensorManager?.unregisterListener(motionListener)
        cameraProvider?.unbindAll()
        screenReceiver?.let { unregisterReceiver(it) }
        menu?.let { windowManager?.removeView(it) }; menu = null
        ball?.let { windowManager?.removeView(it) }; ball = null
        hidePointer()
        executor.execute {
            engine.reset()
            pointerEngine.reset()
            detector?.close(); detector = null
            reusableBitmap?.recycle(); reusableBitmap = null
            orientedCanvas = null
            orientedBitmap?.recycle(); orientedBitmap = null
            packedPixels = null; rowBytes = null
            executor.shutdown()
        }
        super.onDestroy()
    }
}
