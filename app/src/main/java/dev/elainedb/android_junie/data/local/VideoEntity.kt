package dev.elainedb.android_junie.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelTitle: String,
    val publishedAt: String,
    val thumbnailUrl: String,
    val tags: String?, // comma-separated
    val country: String?,
    val city: String?,
    val latitude: Double?,
    val longitude: Double?,
    val recordingDate: String?,
    val lastUpdated: Long // epoch millis
)