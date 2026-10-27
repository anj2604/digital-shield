package com.digitalshield.data

import android.content.Context

data class TrustedContact(val name: String, val phone: String, val consent: Boolean)

object TrustedContactStore {
    private const val PREFS = "trusted_contact"

    fun save(context: Context, c: TrustedContact) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", c.name).putString("phone", c.phone)
            .putBoolean("consent", c.consent).apply()
    }

    fun load(context: Context): TrustedContact? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val phone = p.getString("phone", null) ?: return null
        return TrustedContact(p.getString("name", "") ?: "", phone, p.getBoolean("consent", false))
    }
}