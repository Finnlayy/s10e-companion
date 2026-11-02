package dev.s10e.companion.kid

import android.content.Context
import android.content.SharedPreferences

class SharedPrefsKeyValue(context: Context) : KeyValue {
    private val prefs: SharedPreferences = context.getSharedPreferences("s10e_companion_prefs", Context.MODE_PRIVATE)

    override fun getString(key: String, defValue: String?): String? = prefs.getString(key, defValue)

    override fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getLong(key: String, defValue: Long): Long = prefs.getLong(key, defValue)

    override fun setLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
}
