package com.reink.data.model

data class Feed(
    val id: Long = 0,
    val title: String,
    val url: String,
    val siteUrl: String = "",
    val requiresAuth: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)
