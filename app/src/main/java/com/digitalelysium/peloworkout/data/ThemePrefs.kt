// data/ThemePrefs.kt
package com.digitalelysium.peloworkout.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.digitalelysium.peloworkout.ui.theme.ThemeOption
import kotlinx.coroutines.flow.map

val Context.themeDataStore by preferencesDataStore("settings")
private val THEME_KEY = stringPreferencesKey("theme_option")

fun themeFlow(ctx: Context) = ctx.themeDataStore.data.map { prefs ->
    when (prefs[THEME_KEY]) {
        ThemeOption.Light.name -> ThemeOption.Light
        ThemeOption.Dark.name  -> ThemeOption.Dark
        else                   -> ThemeOption.System
    }
}

suspend fun setTheme(ctx: Context, option: ThemeOption) {
    ctx.themeDataStore.edit { it[THEME_KEY] = option.name }
}
