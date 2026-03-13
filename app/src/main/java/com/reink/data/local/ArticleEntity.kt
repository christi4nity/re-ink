package com.reink.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.reink.data.model.Article

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["feedId"]),
        Index(value = ["publishedAt"]),
        Index(value = ["url"], unique = true),
        Index(value = ["emailMessageId"]),
    ],
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val title: String,
    val author: String,
    val url: String,
    val publishedAt: Long,
    val summary: String,
    val contentHtml: String,
    val contentStatus: String = Article.CONTENT_FULL,
    val isRead: Boolean,
    val emailMessageId: String? = null,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val modifiedAt: Long = 0,
) {
    fun toModel(): Article = Article(
        id = id,
        feedId = feedId,
        title = title,
        author = author,
        url = url,
        publishedAt = publishedAt,
        summary = summary,
        contentHtml = contentHtml,
        contentStatus = contentStatus,
        isRead = isRead,
        emailMessageId = emailMessageId,
        isArchived = isArchived,
        archivedAt = archivedAt,
    )

    companion object {
        fun fromModel(article: Article): ArticleEntity = ArticleEntity(
            id = article.id,
            feedId = article.feedId,
            title = article.title,
            author = article.author,
            url = article.url,
            publishedAt = article.publishedAt,
            summary = article.summary,
            contentHtml = article.contentHtml,
            contentStatus = article.contentStatus,
            isRead = article.isRead,
            emailMessageId = article.emailMessageId,
            isArchived = article.isArchived,
            archivedAt = article.archivedAt,
        )
    }
}
