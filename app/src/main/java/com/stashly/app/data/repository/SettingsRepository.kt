package com.stashly.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stashly.app.domain.model.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultKind: MediaKind = MediaKind.VIDEO,
    /** "BEST", "LOWEST" or target height such as "720". */
    val defaultQuality: String = "BEST",
    val defaultFolder: String = "",
    val wifiOnly: Boolean = false,
    val notifications: Boolean = true,
    /** null = user has not decided yet (we show a consent card). */
    val clipboardDetection: Boolean? = null,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val kind = stringPreferencesKey("default_kind")
        val quality = stringPreferencesKey("default_quality")
        val folder = stringPreferencesKey("default_folder")
        val wifi = booleanPreferencesKey("wifi_only")
        val notif = booleanPreferencesKey("notifications")
        val clip = booleanPreferencesKey("clipboard_detection")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            defaultKind = p[K.kind]?.let { runCatching { MediaKind.valueOf(it) }.getOrNull() } ?: MediaKind.VIDEO,
            defaultQuality = p[K.quality] ?: "BEST",
            defaultFolder = p[K.folder] ?: "",
            wifiOnly = p[K.wifi] ?: false,
            notifications = p[K.notif] ?: true,
            clipboardDetection = p[K.clip],
        )
    }

    suspend fun current(): AppSettings = settings.first()

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) { context.dataStore.edit { it[key] = value } }
    suspend fun setTheme(v: ThemeMode) = set(K.theme, v.name)
    suspend fun setDefaultKind(v: MediaKind) = set(K.kind, v.name)
    suspend fun setDefaultQuality(v: String) = set(K.quality, v)
    suspend fun setDefaultFolder(v: String) = set(K.folder, v)
    suspend fun setWifiOnly(v: Boolean) = set(K.wifi, v)
    suspend fun setNotifications(v: Boolean) = set(K.notif, v)
    suspend fun setClipboardDetection(v: Boolean) = set(K.clip, v)
}
