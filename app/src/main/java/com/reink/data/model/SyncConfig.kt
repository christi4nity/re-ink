package com.reink.data.model

data class SyncConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val apiKey: String = "",
    val deviceId: String = "",
) {
    val isConfigured: Boolean
        get() = enabled && serverUrl.isNotBlank() && apiKey.isNotBlank()
}
