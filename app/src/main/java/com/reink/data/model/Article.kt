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
    val isRead: Boolean = false,
)
