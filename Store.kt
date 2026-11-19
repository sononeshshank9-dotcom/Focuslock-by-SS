package com.focuslock

import android.content.Context

object Store {
    private fun p(c: Context) = c.getSharedPreferences("focuslock", Context.MODE_PRIVATE)

    fun blocked(c: Context): Set<String> =
        p(c).getStringSet("blocked", emptySet()) ?: emptySet()

    fun setBlocked(c: Context, s: Set<String>) {
        p(c).edit().putStringSet("blocked", HashSet(s)).apply()
    }

    fun endTime(c: Context): Long = p(c).getLong("end", 0L)

    fun setEndTime(c: Context, t: Long) {
        p(c).edit().putLong("end", t).apply()
    }

    fun locked(c: Context): Boolean = p(c).getBoolean("locked", false)

    fun setLocked(c: Context, v: Boolean) {
        p(c).edit().putBoolean("locked", v).apply()
    }

    fun active(c: Context): Boolean = System.currentTimeMillis() < endTime(c)
}
