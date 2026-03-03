package com.reink.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reink.data.model.ReadingPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        private val KEY_FONT_SIZE = intPreferencesKey("font_size")
        private val KEY_LINE_HEIGHT = floatPreferencesKey("line_height")
        private val KEY_MARGIN_HORIZONTAL = intPreferencesKey("margin_horizontal")
        private val KEY_MARGIN_VERTICAL = intPreferencesKey("margin_vertical")
        private val KEY_TEXT_ALIGN = stringPreferencesKey("text_align")
        private val KEY_PAGINATION_MODE = stringPreferencesKey("pagination_mode")
        private val KEY_SUBSTACK_SID = stringPreferencesKey("substack_sid")
        private val KEY_LAST_EMAIL_SYNC = longPreferencesKey("last_email_sync")
        private val KEY_EMAIL_SYNC_ENABLED = booleanPreferencesKey("email_sync_enabled")
    }

    fun observeReadingPreferences(): Flow<ReadingPreferences> =
        dataStore.data.map { prefs ->
            ReadingPreferences(
                fontFamily = prefs[KEY_FONT_FAMILY] ?: ReadingPreferences().fontFamily,
                fontSize = prefs[KEY_FONT_SIZE] ?: ReadingPreferences().fontSize,
                lineHeight = prefs[KEY_LINE_HEIGHT] ?: ReadingPreferences().lineHeight,
                marginHorizontal = prefs[KEY_MARGIN_HORIZONTAL] ?: ReadingPreferences().marginHorizontal,
                marginVertical = prefs[KEY_MARGIN_VERTICAL] ?: ReadingPreferences().marginVertical,
                textAlign = prefs[KEY_TEXT_ALIGN] ?: ReadingPreferences().textAlign,
                paginationMode = prefs[KEY_PAGINATION_MODE] ?: ReadingPreferences().paginationMode,
            )
        }

    suspend fun updateReadingPreferences(prefs: ReadingPreferences) {
        dataStore.edit { store ->
            store[KEY_FONT_FAMILY] = prefs.fontFamily
            store[KEY_FONT_SIZE] = prefs.fontSize
            store[KEY_LINE_HEIGHT] = prefs.lineHeight
            store[KEY_MARGIN_HORIZONTAL] = prefs.marginHorizontal
            store[KEY_MARGIN_VERTICAL] = prefs.marginVertical
            store[KEY_TEXT_ALIGN] = prefs.textAlign
            store[KEY_PAGINATION_MODE] = prefs.paginationMode
        }
    }

    fun observeSubstackSid(): Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_SUBSTACK_SID] ?: "" }

    suspend fun getSubstackSid(): String =
        dataStore.data.first()[KEY_SUBSTACK_SID] ?: ""

    suspend fun setSubstackSid(sid: String) {
        dataStore.edit { store -> store[KEY_SUBSTACK_SID] = sid }
    }

    fun observeEmailSyncEnabled(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_EMAIL_SYNC_ENABLED] ?: false }

    suspend fun setEmailSyncEnabled(enabled: Boolean) {
        dataStore.edit { store -> store[KEY_EMAIL_SYNC_ENABLED] = enabled }
    }

    suspend fun getLastEmailSync(): Long =
        dataStore.data.first()[KEY_LAST_EMAIL_SYNC] ?: 0L

    suspend fun setLastEmailSync(timestamp: Long) {
        dataStore.edit { store -> store[KEY_LAST_EMAIL_SYNC] = timestamp }
    }
}
