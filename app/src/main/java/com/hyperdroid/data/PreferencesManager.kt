package com.hyperdroid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hyperdroid_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val SETUP_STEP = intPreferencesKey("setup_current_step")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val isSetupCompleted: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.SETUP_COMPLETED] ?: false }

    val currentSetupStep: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[Keys.SETUP_STEP] ?: 0 }

    val themeMode: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.THEME_MODE] ?: "system" }

    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SETUP_COMPLETED] = completed
        }
    }

    suspend fun setSetupStep(step: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SETUP_STEP] = step
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }
}
