package de.famail.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val splitFraction: Float = DEFAULT_SPLIT,
    val rememberSplit: Boolean = true,
    val loadRemoteImages: Boolean = false,
    val syncIntervalMinutes: Int = 15,
    val notificationsEnabled: Boolean = true,
    val notifyOnlyImportant: Boolean = false,
    val hideContentOnLockscreen: Boolean = false,
    val fontScale: Float = 1f
) {
    companion object {
        const val DEFAULT_SPLIT = 0.4f
        const val MIN_SPLIT = 0.2f
        const val MAX_SPLIT = 0.8f
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val split = floatPreferencesKey("split")
        val rememberSplit = booleanPreferencesKey("remember_split")
        val remoteImages = booleanPreferencesKey("remote_images")
        val syncInterval = intPreferencesKey("sync_interval")
        val notifications = booleanPreferencesKey("notifications")
        val notifyImportant = booleanPreferencesKey("notify_important")
        val hideLockscreen = booleanPreferencesKey("hide_lockscreen")
        val fontScale = floatPreferencesKey("font_scale")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(this[Keys.theme] ?: "LIGHT") }
            .getOrDefault(ThemeMode.LIGHT),
        splitFraction = (this[Keys.split] ?: AppSettings.DEFAULT_SPLIT)
            .coerceIn(AppSettings.MIN_SPLIT, AppSettings.MAX_SPLIT),
        rememberSplit = this[Keys.rememberSplit] ?: true,
        loadRemoteImages = this[Keys.remoteImages] ?: false,
        syncIntervalMinutes = this[Keys.syncInterval] ?: 15,
        notificationsEnabled = this[Keys.notifications] ?: true,
        notifyOnlyImportant = this[Keys.notifyImportant] ?: false,
        hideContentOnLockscreen = this[Keys.hideLockscreen] ?: false,
        fontScale = this[Keys.fontScale] ?: 1f
    )

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setSplit(fraction: Float) = context.dataStore.edit {
        it[Keys.split] = fraction.coerceIn(AppSettings.MIN_SPLIT, AppSettings.MAX_SPLIT)
    }
    suspend fun setRememberSplit(v: Boolean) = context.dataStore.edit { it[Keys.rememberSplit] = v }
    suspend fun setLoadRemoteImages(v: Boolean) = context.dataStore.edit { it[Keys.remoteImages] = v }
    suspend fun setSyncInterval(minutes: Int) = context.dataStore.edit { it[Keys.syncInterval] = minutes }
    suspend fun setNotifications(v: Boolean) = context.dataStore.edit { it[Keys.notifications] = v }
    suspend fun setNotifyOnlyImportant(v: Boolean) = context.dataStore.edit { it[Keys.notifyImportant] = v }
    suspend fun setHideLockscreen(v: Boolean) = context.dataStore.edit { it[Keys.hideLockscreen] = v }
    suspend fun setFontScale(v: Float) = context.dataStore.edit { it[Keys.fontScale] = v.coerceIn(0.85f, 1.3f) }
}
