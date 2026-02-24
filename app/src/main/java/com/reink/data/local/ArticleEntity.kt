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
    val isRead: Boolean,
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
        isRead = isRead,
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
            isRead = article.isRead,
        )
    }
}
