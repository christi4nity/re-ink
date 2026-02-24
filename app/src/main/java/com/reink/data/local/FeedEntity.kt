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
    val requiresAuth: Boolean,
    val addedAt: Long,
) {
    fun toModel(): Feed = Feed(
        id = id,
        title = title,
        url = url,
        siteUrl = siteUrl,
        requiresAuth = requiresAuth,
        addedAt = addedAt,
    )

    companion object {
        fun fromModel(feed: Feed): FeedEntity = FeedEntity(
            id = feed.id,
            title = feed.title,
            url = feed.url,
            siteUrl = feed.siteUrl,
            requiresAuth = feed.requiresAuth,
            addedAt = feed.addedAt,
        )
    }
}
