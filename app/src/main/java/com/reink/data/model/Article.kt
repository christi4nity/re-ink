package com.reink.data.model

data class Article(
    val id: Long = 0,
    val feedId: Long,
    val title: String,
    val author: String = "",
    val url: String,
    val publishedAt: Long,
    val summary: String = "",
    val contentHtml: String = "",
    val contentStatus: String = CONTENT_FULL,
    val isRead: Boolean = false,
    val emailMessageId: String? = null,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
) {
    companion object {
        const val CONTENT_FULL = "full"
        const val CONTENT_TRUNCATED = "truncated"
        const val CONTENT_EXTRACTED = "extracted"
        const val CONTENT_EMAIL = "email"
        const val CONTENT_FAILED = "failed"
    }
}
