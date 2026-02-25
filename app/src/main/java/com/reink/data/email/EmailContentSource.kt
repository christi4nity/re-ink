package com.reink.data.email

data class EmailArticle(
    val subject: String,
    val senderAddress: String,
    val senderName: String,
    val receivedAt: Long,
    val contentHtml: String,
    val viewOnlineUrl: String?,
    val messageId: String,
)

interface EmailContentSource {
    suspend fun fetchNewArticles(sinceTimestamp: Long): Result<List<EmailArticle>>
    suspend fun testConnection(): Result<String>
}
