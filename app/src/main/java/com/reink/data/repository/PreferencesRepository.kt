package com.reink.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.reink.data.model.AppUpdate
import com.reink.data.model.CloudQueueConfig
import com.reink.data.model.ReadingPreferences
import com.reink.data.model.SyncConfig
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
        private val KEY_LAST_EMAIL_SYNC = longPreferencesKey("last_email_sync")
        private val KEY_EMAIL_SYNC_ENABLED = booleanPreferencesKey("email_sync_enabled")
        private val KEY_CLOUD_QUEUE_ENABLED = booleanPreferencesKey("cloud_queue_enabled")
        private val KEY_CLOUD_QUEUE_ID = stringPreferencesKey("cloud_queue_id")
        private val KEY_CLOUD_QUEUE_BASE_URL = stringPreferencesKey("cloud_queue_base_url")
        private val KEY_SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        private val KEY_SYNC_SERVER_URL = stringPreferencesKey("sync_server_url")
        private val KEY_SYNC_API_KEY = stringPreferencesKey("sync_api_key")
        private val KEY_SYNC_DEVICE_ID = stringPreferencesKey("sync_device_id")
        private val KEY_SYNC_LAST_SYNCED_AT = longPreferencesKey("sync_last_synced_at")
        private val KEY_PREFS_MODIFIED_AT = longPreferencesKey("prefs_modified_at")
        private val KEY_UPDATE_VERSION = stringPreferencesKey("update_version")
        private val KEY_UPDATE_DOWNLOAD_URL = stringPreferencesKey("update_download_url")
        private val KEY_UPDATE_RELEASE_NOTES = stringPreferencesKey("update_release_notes")
        private val KEY_UPDATE_DISMISSED_VERSION = stringPreferencesKey("update_dismissed_version")
        private val KEY_UPDATE_READY = booleanPreferencesKey("update_ready")
        private val KEY_ALLOWED_SENDER_DOMAINS = stringSetPreferencesKey("allowed_sender_domains")
        private val KEY_DOMAINS_SEEDED = booleanPreferencesKey("allowed_sender_domains_seeded")
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
            store[KEY_PREFS_MODIFIED_AT] = System.currentTimeMillis()
        }
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

    fun observeCloudQueueConfig(): Flow<CloudQueueConfig> =
        dataStore.data.map { prefs ->
            CloudQueueConfig(
                enabled = prefs[KEY_CLOUD_QUEUE_ENABLED] ?: false,
                queueId = prefs[KEY_CLOUD_QUEUE_ID] ?: "",
                baseUrl = prefs[KEY_CLOUD_QUEUE_BASE_URL] ?: "",
            )
        }

    suspend fun getCloudQueueConfig(): CloudQueueConfig {
        val prefs = dataStore.data.first()
        return CloudQueueConfig(
            enabled = prefs[KEY_CLOUD_QUEUE_ENABLED] ?: false,
            queueId = prefs[KEY_CLOUD_QUEUE_ID] ?: "",
            baseUrl = prefs[KEY_CLOUD_QUEUE_BASE_URL] ?: "",
        )
    }

    suspend fun setCloudQueueConfig(config: CloudQueueConfig) {
        dataStore.edit { store ->
            store[KEY_CLOUD_QUEUE_ENABLED] = config.enabled
            store[KEY_CLOUD_QUEUE_ID] = config.queueId
            store[KEY_CLOUD_QUEUE_BASE_URL] = config.baseUrl
        }
    }

    suspend fun clearCloudQueue() {
        dataStore.edit { store ->
            store[KEY_CLOUD_QUEUE_ENABLED] = false
            store.remove(KEY_CLOUD_QUEUE_ID)
            store.remove(KEY_CLOUD_QUEUE_BASE_URL)
        }
    }

    fun observeSyncConfig(): Flow<SyncConfig> =
        dataStore.data.map { prefs ->
            SyncConfig(
                enabled = prefs[KEY_SYNC_ENABLED] ?: false,
                serverUrl = prefs[KEY_SYNC_SERVER_URL] ?: "",
                apiKey = prefs[KEY_SYNC_API_KEY] ?: "",
                deviceId = prefs[KEY_SYNC_DEVICE_ID] ?: "",
            )
        }

    suspend fun getSyncConfig(): SyncConfig {
        val prefs = dataStore.data.first()
        return SyncConfig(
            enabled = prefs[KEY_SYNC_ENABLED] ?: false,
            serverUrl = prefs[KEY_SYNC_SERVER_URL] ?: "",
            apiKey = prefs[KEY_SYNC_API_KEY] ?: "",
            deviceId = prefs[KEY_SYNC_DEVICE_ID] ?: "",
        )
    }

    suspend fun setSyncConfig(config: SyncConfig) {
        dataStore.edit { store ->
            store[KEY_SYNC_ENABLED] = config.enabled
            store[KEY_SYNC_SERVER_URL] = config.serverUrl
            store[KEY_SYNC_API_KEY] = config.apiKey
            store[KEY_SYNC_DEVICE_ID] = config.deviceId
        }
    }

    suspend fun clearSyncConfig() {
        dataStore.edit { store ->
            store[KEY_SYNC_ENABLED] = false
            store.remove(KEY_SYNC_SERVER_URL)
            store.remove(KEY_SYNC_API_KEY)
            store.remove(KEY_SYNC_DEVICE_ID)
            store.remove(KEY_SYNC_LAST_SYNCED_AT)
        }
    }

    suspend fun getSyncLastSyncedAt(): Long =
        dataStore.data.first()[KEY_SYNC_LAST_SYNCED_AT] ?: 0L

    suspend fun setSyncLastSyncedAt(timestamp: Long) {
        dataStore.edit { store -> store[KEY_SYNC_LAST_SYNCED_AT] = timestamp }
    }

    suspend fun getReadingPreferences(): ReadingPreferences {
        val prefs = dataStore.data.first()
        return ReadingPreferences(
            fontFamily = prefs[KEY_FONT_FAMILY] ?: ReadingPreferences().fontFamily,
            fontSize = prefs[KEY_FONT_SIZE] ?: ReadingPreferences().fontSize,
            lineHeight = prefs[KEY_LINE_HEIGHT] ?: ReadingPreferences().lineHeight,
            marginHorizontal = prefs[KEY_MARGIN_HORIZONTAL] ?: ReadingPreferences().marginHorizontal,
            marginVertical = prefs[KEY_MARGIN_VERTICAL] ?: ReadingPreferences().marginVertical,
            textAlign = prefs[KEY_TEXT_ALIGN] ?: ReadingPreferences().textAlign,
            paginationMode = prefs[KEY_PAGINATION_MODE] ?: ReadingPreferences().paginationMode,
        )
    }

    suspend fun getPreferencesModifiedAt(): Long =
        dataStore.data.first()[KEY_PREFS_MODIFIED_AT] ?: 0L

    suspend fun setPreferencesModifiedAt(timestamp: Long) {
        dataStore.edit { store -> store[KEY_PREFS_MODIFIED_AT] = timestamp }
    }

    fun observeAvailableUpdate(): Flow<AppUpdate?> =
        dataStore.data.map { prefs ->
            val version = prefs[KEY_UPDATE_VERSION] ?: return@map null
            val dismissedVersion = prefs[KEY_UPDATE_DISMISSED_VERSION]
            if (version == dismissedVersion) return@map null
            AppUpdate(
                versionName = version,
                downloadUrl = prefs[KEY_UPDATE_DOWNLOAD_URL] ?: return@map null,
                releaseNotes = prefs[KEY_UPDATE_RELEASE_NOTES] ?: "",
            )
        }

    suspend fun setAvailableUpdate(versionName: String, downloadUrl: String, releaseNotes: String) {
        dataStore.edit { store ->
            store[KEY_UPDATE_VERSION] = versionName
            store[KEY_UPDATE_DOWNLOAD_URL] = downloadUrl
            store[KEY_UPDATE_RELEASE_NOTES] = releaseNotes
        }
    }

    suspend fun dismissUpdate(versionName: String) {
        dataStore.edit { store ->
            store[KEY_UPDATE_DISMISSED_VERSION] = versionName
            store[KEY_UPDATE_READY] = false
        }
    }

    fun observeUpdateReady(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_UPDATE_READY] ?: false }

    suspend fun setUpdateReady(ready: Boolean) {
        dataStore.edit { store -> store[KEY_UPDATE_READY] = ready }
    }

    fun observeAllowedSenderDomains(): Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet() }

    suspend fun getAllowedSenderDomains(): Set<String> =
        dataStore.data.first()[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet()

    suspend fun addAllowedSenderDomain(domain: String) {
        val cleaned = domain
            .trim()
            .lowercase()
            .substringAfter("@")  // Handle user@domain.com -> domain.com
        if (cleaned.isBlank() || '.' !in cleaned) return  // Basic validation
        dataStore.edit { store ->
            val current = store[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet()
            store[KEY_ALLOWED_SENDER_DOMAINS] = current + cleaned
        }
    }

    suspend fun removeAllowedSenderDomain(domain: String) {
        dataStore.edit { store ->
            val current = store[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet()
            store[KEY_ALLOWED_SENDER_DOMAINS] = current - domain.lowercase()
        }
    }

    suspend fun seedDefaultAllowedDomains() {
        val hasSeeded = dataStore.data.first()[KEY_DOMAINS_SEEDED] ?: false
        if (!hasSeeded) {
            dataStore.edit { store ->
                val current = store[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet()
                store[KEY_ALLOWED_SENDER_DOMAINS] = current + "substack.com"
                store[KEY_DOMAINS_SEEDED] = true
            }
        }
    }
}
