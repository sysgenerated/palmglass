package io.github.sysgenerated.palmglass.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    /** Open the touchpad automatically when the glasses are plugged in. Off by default. */
    val autoStart: Boolean = false,
    /** Drive the phone panel at minimum brightness while the touchpad is open. */
    val dimScreen: Boolean = true,
    /** Two-finger scroll moves content with the fingers (true) or the viewport (false). */
    val naturalScroll: Boolean = true,
    /** Switch back to the previous keyboard when a field focuses while the touchpad is closed. */
    val handBackKeyboard: Boolean = true,
    /** Cursor speed multiplier. */
    val sensitivity: Float = 1.0f,
)

class Prefs(private val context: Context) {
    private object Keys {
        val autoStart = booleanPreferencesKey("auto_start")
        val dimScreen = booleanPreferencesKey("dim_screen")
        val naturalScroll = booleanPreferencesKey("natural_scroll")
        val handBackKeyboard = booleanPreferencesKey("hand_back_keyboard")
        val sensitivity = floatPreferencesKey("sensitivity")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            autoStart = p[Keys.autoStart] ?: false,
            dimScreen = p[Keys.dimScreen] ?: true,
            naturalScroll = p[Keys.naturalScroll] ?: true,
            handBackKeyboard = p[Keys.handBackKeyboard] ?: true,
            sensitivity = p[Keys.sensitivity] ?: 1.0f,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setAutoStart(v: Boolean) = context.dataStore.edit { it[Keys.autoStart] = v }
    suspend fun setDimScreen(v: Boolean) = context.dataStore.edit { it[Keys.dimScreen] = v }
    suspend fun setNaturalScroll(v: Boolean) = context.dataStore.edit { it[Keys.naturalScroll] = v }
    suspend fun setHandBackKeyboard(v: Boolean) = context.dataStore.edit { it[Keys.handBackKeyboard] = v }
    suspend fun setSensitivity(v: Float) = context.dataStore.edit { it[Keys.sensitivity] = v }
}
