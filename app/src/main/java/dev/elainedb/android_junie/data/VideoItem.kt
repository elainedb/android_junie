package dev.elainedb.android_junie.data

data class VideoItem(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val publishedAt: String, // ISO 8601; display YYYY-MM-DD via substring(0, 10)
    val thumbnailUrl: String,
    val tags: List<String>? = null,
    val country: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val recordingDate: String? = null
)
