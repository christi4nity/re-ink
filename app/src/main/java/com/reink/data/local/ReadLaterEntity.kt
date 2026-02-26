package com.reink.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.reink.data.model.FetchStatus
import com.reink.data.model.ReadLaterItem
import com.reink.data.remote.ArticleExtractor

@Entity(tableName = "read_later")
data class ReadLaterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val contentHtml: String,
    val sourceArticleId: Long?,
    val savedAt: Long,
    val fetchStatus: String,
    val isRead: Boolean,
    val sourceDomain: String? = null,
) {
    fun toModel(): ReadLaterItem = ReadLaterItem(
        id = id,
        url = url,
        title = title,
        contentHtml = contentHtml,
        sourceArticleId = sourceArticleId,
        savedAt = savedAt,
        fetchStatus = FetchStatus.valueOf(fetchStatus),
        isRead = isRead,
        sourceDomain = sourceDomain ?: ArticleExtractor.extractDomain(url),
    )

    companion object {
        fun fromModel(item: ReadLaterItem): ReadLaterEntity = ReadLaterEntity(
            id = item.id,
            url = item.url,
            title = item.title,
            contentHtml = item.contentHtml,
            sourceArticleId = item.sourceArticleId,
            savedAt = item.savedAt,
            fetchStatus = item.fetchStatus.name,
            isRead = item.isRead,
            sourceDomain = item.sourceDomain,
        )
    }
}
