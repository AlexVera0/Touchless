package com.touchless.app

import android.content.Context

class DisplayPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) { prefs.edit().putBoolean("dark_mode", value).apply() }

    var english: Boolean
        get() = prefs.getBoolean("english", false)
        set(value) { prefs.edit().putBoolean("english", value).apply() }

    var selectedTab: String
        get() = prefs.getString("selected_tab", "home") ?: "home"
        set(value) { prefs.edit().putString("selected_tab", value).apply() }

    var themeColor: String
        get() = prefs.getString("theme_color", "blue") ?: "blue"
        set(value) { prefs.edit().putString("theme_color", value).apply() }

    fun reset() { prefs.edit().clear().commit() }
}
