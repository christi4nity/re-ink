package com.reink.data.model

data class ReadLaterItem(
    val id: Long = 0,
    val url: String,
    val title: String = "",
    val contentHtml: String = "",
    val sourceArticleId: Long? = null,
    val savedAt: Long = System.currentTimeMillis(),
    val fetchStatus: FetchStatus = FetchStatus.PENDING,
    val isRead: Boolean = false,
)
