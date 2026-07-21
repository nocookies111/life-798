package com.water.widget

import android.content.Context

/** 用户可选的界面模式。默认跟随系统；修改后由 Activity recreate 立即生效。 */
enum class AppThemeMode(val storageValue: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

object ThemeSettings {
    private const val PREFS = "appearance_settings"
    private const val KEY_THEME_MODE = "theme_mode"

    fun mode(context: Context): AppThemeMode = AppThemeMode.fromStorage(
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.storageValue)
    )

    fun setMode(context: Context, mode: AppThemeMode) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.storageValue)
            .apply()
    }

    fun isDark(context: Context): Boolean = when (mode(context)) {
        AppThemeMode.SYSTEM -> (context.resources.configuration.uiMode
            and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
}
