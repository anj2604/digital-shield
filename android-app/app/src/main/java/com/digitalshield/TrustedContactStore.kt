package com.digitalshield.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TrustedContact(val name: String, val phone: String, val consent: Boolean = true)

object TrustedContactStore {
    const val MAX = 5
    private const val PREFS = "trusted_contact"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun digits(p: String) = p.filter(Char::isDigit).takeLast(10)

    fun loadAll(c: Context): List<TrustedContact> {
        val p = prefs(c)
        val json = p.getString("list", null)
        if (json == null) {
            // Carry over the old single trusted contact, if there was one
            val phone = p.getString("phone", null) ?: return emptyList()
            val old = listOf(TrustedContact(p.getString("name", "") ?: "", phone, p.getBoolean("consent", true)))
            saveAll(c, old)
            return old
        }
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TrustedContact(o.optString("name"), o.getString("phone"), o.optBoolean("consent", true))
        }
    }

    private fun saveAll(c: Context, list: List<TrustedContact>) {
        val arr = JSONArray()
        list.take(MAX).forEach { arr.put(JSONObject().put("name", it.name).put("phone", it.phone).put("consent", it.consent)) }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    /** Returns a short message for the user. */
    fun add(c: Context, contact: TrustedContact): String {
        val list = loadAll(c)
        return when {
            digits(contact.phone).length < 10 -> "Please enter a valid 10-digit phone number"
            list.size >= MAX -> "You can add up to $MAX trusted contacts"
            list.any { digits(it.phone) == digits(contact.phone) } -> "${contact.name.ifBlank { contact.phone }} is already in your list"
            else -> { saveAll(c, list + contact); "${contact.name.ifBlank { contact.phone }} added" }
        }
    }

    fun remove(c: Context, phone: String) = saveAll(c, loadAll(c).filterNot { digits(it.phone) == digits(phone) })

    fun setConsent(c: Context, phone: String, on: Boolean) =
        saveAll(c, loadAll(c).map { if (digits(it.phone) == digits(phone)) it.copy(consent = on) else it })
}