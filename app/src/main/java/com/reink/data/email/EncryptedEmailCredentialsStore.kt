package com.reink.data.email

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedEmailCredentialsStore @Inject constructor(
    @ApplicationContext context: Context,
) : EmailCredentialsStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "email_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun get(): EmailCredentials? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return EmailCredentials(
            host = host,
            port = prefs.getInt(KEY_PORT, 993),
            username = username,
            password = password,
            folderName = prefs.getString(KEY_FOLDER, "INBOX") ?: "INBOX",
        )
    }

    override fun save(credentials: EmailCredentials) {
        prefs.edit()
            .putString(KEY_HOST, credentials.host)
            .putInt(KEY_PORT, credentials.port)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .putString(KEY_FOLDER, credentials.folderName)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun isConfigured(): Boolean =
        prefs.getString(KEY_HOST, null) != null

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_FOLDER = "folder"
    }
}
