package com.reink.ui.home

import com.reink.data.model.Article
import com.reink.data.model.ReadLaterItem

sealed interface HomeItem {
    val id: Long
    val title: String
    val isRead: Boolean
    val timestamp: Long

    data class ArticleItem(
        val article: Article,
        val feedTitle: String,
    ) : HomeItem {
        override val id: Long get() = article.id
        override val title: String get() = article.title
        override val isRead: Boolean get() = article.isRead
        override val timestamp: Long get() = article.publishedAt
    }

    data class ReadLaterHomeItem(
        val item: ReadLaterItem,
    ) : HomeItem {
        override val id: Long get() = item.id
        override val title: String get() = item.title.ifBlank { item.url }
        override val isRead: Boolean get() = item.isRead
        override val timestamp: Long get() = item.savedAt
    }
}
