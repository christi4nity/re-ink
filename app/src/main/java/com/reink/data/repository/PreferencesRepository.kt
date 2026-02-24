package com.reink.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        private val KEY_TEXT_ALIGN = stringPreferencesKey("text_align")
        private val KEY_SUBSTACK_SID = stringPreferencesKey("substack_sid")
    }

    fun observeReadingPreferences(): Flow<ReadingPreferences> =
        dataStore.data.map { prefs ->
            ReadingPreferences(
                fontFamily = prefs[KEY_FONT_FAMILY] ?: "Literata",
                fontSize = prefs[KEY_FONT_SIZE] ?: 18,
                lineHeight = prefs[KEY_LINE_HEIGHT] ?: 1.6f,
                marginHorizontal = prefs[KEY_MARGIN_HORIZONTAL] ?: 16,
                textAlign = prefs[KEY_TEXT_ALIGN] ?: "left",
            )
        }

    suspend fun updateReadingPreferences(prefs: ReadingPreferences) {
        dataStore.edit { store ->
            store[KEY_FONT_FAMILY] = prefs.fontFamily
            store[KEY_FONT_SIZE] = prefs.fontSize
            store[KEY_LINE_HEIGHT] = prefs.lineHeight
            store[KEY_MARGIN_HORIZONTAL] = prefs.marginHorizontal
            store[KEY_TEXT_ALIGN] = prefs.textAlign
        }
    }

    fun observeSubstackSid(): Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_SUBSTACK_SID] ?: "" }

    suspend fun getSubstackSid(): String =
        dataStore.data.first()[KEY_SUBSTACK_SID] ?: ""

    suspend fun setSubstackSid(sid: String) {
        dataStore.edit { store -> store[KEY_SUBSTACK_SID] = sid }
    }
}
