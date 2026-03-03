package com.reink.data.model

data class CloudQueueConfig(
    val enabled: Boolean = false,
    val queueId: String = "",
    val baseUrl: String = "",
) {
    val shareUrl: String
        get() = if (enabled && queueId.isNotBlank() && baseUrl.isNotBlank()) {
            "$baseUrl/q/$queueId/items"
        } else {
            ""
        }

    val setupUrl: String
        get() = if (enabled && queueId.isNotBlank() && baseUrl.isNotBlank()) {
            "$baseUrl/q/$queueId/setup"
        } else {
            ""
        }

    val isConfigured: Boolean
        get() = enabled && queueId.isNotBlank()
}
