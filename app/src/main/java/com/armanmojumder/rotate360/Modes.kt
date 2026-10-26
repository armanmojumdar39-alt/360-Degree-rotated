package com.armanmojumder.rotate360

import android.content.Context
import android.content.pm.ActivityInfo

data class RotMode(val id: Int, val title: String, val desc: String, val orientation: Int)

object Modes {
    val all = listOf(
        RotMode(0, "Auto (system)", "Let Android decide", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
        RotMode(1, "Free 360° rotation", "All 4 directions, even if auto-rotate is off", ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR),
        RotMode(2, "Portrait", "Lock upright", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
        RotMode(3, "Landscape", "Lock sideways (left)", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
        RotMode(4, "Reverse portrait", "Lock upside-down", ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
        RotMode(5, "Reverse landscape", "Lock sideways (right)", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
    )

    fun byId(id: Int) = all.firstOrNull { it.id == id } ?: all[1]
}

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("rotate360", Context.MODE_PRIVATE)
    fun modeId(c: Context) = p(c).getInt("mode", 1)
    fun setMode(c: Context, id: Int) = p(c).edit().putInt("mode", id).apply()
    fun bubble(c: Context) = p(c).getBoolean("bubble", true)
    fun setBubble(c: Context, on: Boolean) = p(c).edit().putBoolean("bubble", on).apply()
}
