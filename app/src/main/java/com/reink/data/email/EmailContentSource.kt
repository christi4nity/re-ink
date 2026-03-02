package com.reink.data.email

data class EmailArticle(
    val subject: String,
    val subtitle: String,
    val senderAddress: String,
    val senderName: String,
    val substackSubdomain: String,
    val receivedAt: Long,
    val contentHtml: String,
    val viewOnlineUrl: String?,
    val messageId: String,
)

interface EmailContentSource {
    suspend fun fetchNewArticles(sinceTimestamp: Long): Result<List<EmailArticle>>
    fun streamNewArticles(sinceTimestamp: Long): kotlinx.coroutines.flow.Flow<EmailArticle>
    suspend fun testConnection(): Result<String>
}
