package com.reink.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.reink.data.model.Feed

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val siteUrl: String,
    val imageUrl: String? = null,
    val requiresAuth: Boolean,
    val addedAt: Long,
    val authToken: String? = null,
    val substackSubdomain: String? = null,
    val enabledSectionSlugs: String? = null,
    val emailSenderPattern: String? = null,
) {
    fun toModel(): Feed = Feed(
        id = id,
        title = title,
        url = url,
        siteUrl = siteUrl,
        imageUrl = imageUrl,
        requiresAuth = requiresAuth,
        addedAt = addedAt,
        authToken = authToken,
        substackSubdomain = substackSubdomain,
        enabledSectionSlugs = enabledSectionSlugs,
        emailSenderPattern = emailSenderPattern,
    )

    companion object {
        fun fromModel(feed: Feed): FeedEntity = FeedEntity(
            id = feed.id,
            title = feed.title,
            url = feed.url,
            siteUrl = feed.siteUrl,
            imageUrl = feed.imageUrl,
            requiresAuth = feed.requiresAuth,
            addedAt = feed.addedAt,
            authToken = feed.authToken,
            substackSubdomain = feed.substackSubdomain,
            enabledSectionSlugs = feed.enabledSectionSlugs,
            emailSenderPattern = feed.emailSenderPattern,
        )
    }
}
