package com.touchless.app

import android.content.Context

class ConsentPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("consent", Context.MODE_PRIVATE)

    var acceptedPolicyRevision: Int
        get() = prefs.getInt("accepted_policy_revision", 0)
        set(value) { prefs.edit().putInt("accepted_policy_revision", value).apply() }

    var accessibilityDisclosureAccepted: Boolean
        get() = prefs.getBoolean("accessibility_disclosure_accepted", false)
        set(value) { prefs.edit().putBoolean("accessibility_disclosure_accepted", value).apply() }

    fun reset() { prefs.edit().clear().commit() }

    companion object { const val CURRENT_POLICY_REVISION = 1 }
}
