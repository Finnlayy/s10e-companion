package dev.s10e.companion.kid
interface KeyValue {
    fun getString(key: String, defValue: String?): String?
    fun setString(key: String, value: String)
    fun getLong(key: String, defValue: Long): Long
    fun setLong(key: String, value: Long)
}
