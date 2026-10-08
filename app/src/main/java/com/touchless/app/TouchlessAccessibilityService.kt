package com.touchless.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class TouchlessAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: TouchlessAccessibilityService? = null }
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun click(x: Float, y: Float, onFinished: (Boolean) -> Unit): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 70)).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) { onFinished(true) }
                override fun onCancelled(gestureDescription: GestureDescription) { onFinished(false) }
            }, null)
    }

    fun swipe(onFinished: (Boolean) -> Unit): Boolean {
        val display = resources.displayMetrics
        val x = display.widthPixels * .5f
        val startY = display.heightPixels * .70f
        val endY = display.heightPixels * .30f
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        return dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 220)).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) { onFinished(true) }
                override fun onCancelled(gestureDescription: GestureDescription) { onFinished(false) }
            }, null)
    }
}
