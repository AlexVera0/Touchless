package com.touchless.app

import android.content.Context

data class GestureSettings(
    val distancePercent: Int = 4,
    val recognitionTimeMs: Int = 100,
    val sensitivity: Int = 75,
    val intervalMs: Int = 1500,
    val homeGestureEnabled: Boolean = false,
    val pointerEnabled: Boolean = false
)

class GestureSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("gesture_settings", Context.MODE_PRIVATE)
    fun load() = GestureSettings(
        prefs.getInt("distance", 4).coerceIn(2, 20),
        prefs.getInt("time", 100).coerceIn(60, 400),
        prefs.getInt("sensitivity", 75).coerceIn(0, 100),
        prefs.getInt("interval", 1500).coerceIn(1000, 2000),
        prefs.getBoolean("home_gesture_enabled", false),
        prefs.getBoolean("pointer_enabled", false)
    )
    fun save(settings: GestureSettings) {
        prefs.edit().putInt("distance", settings.distancePercent.coerceIn(2, 20))
            .putInt("time", settings.recognitionTimeMs.coerceIn(60, 400))
            .putInt("sensitivity", settings.sensitivity.coerceIn(0, 100))
            .putInt("interval", settings.intervalMs.coerceIn(1000, 2000))
            .putBoolean("home_gesture_enabled", settings.homeGestureEnabled)
            .putBoolean("pointer_enabled", settings.pointerEnabled).apply()
    }
    fun reset() { prefs.edit().clear().commit() }
}
