package dev.elainedb.android_junie.data

import android.content.Context
import dev.elainedb.android_junie.data.local.AppDatabase
import dev.elainedb.android_junie.data.local.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(private val context: Context) {

    private val db by lazy { AppDatabase.get(context) }
    private val dao by lazy { db.videoDao() }

    companion object {
        const val FRESHNESS_THRESHOLD_MS: Long = 24L * 60 * 60 * 1000 // 24 hours
    }

    suspend fun getVideos(forceRefresh: Boolean = false): List<VideoItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastUpdated = dao.getLastUpdated() ?: 0L
        val isFresh = (now - lastUpdated) < FRESHNESS_THRESHOLD_MS
        if (!forceRefresh && isFresh) {
            return@withContext dao.getAll().map { it.toDomain() }
        }
        val fetchedRaw = YouTubeApi.fetchAllVideos()
        val enhanced = addReverseGeocodedLocations(fetchedRaw)
        // Replace cache
        dao.clear()
        dao.insertAll(enhanced.map { it.toEntity(now) })
        return@withContext enhanced
    }

    suspend fun getVideosWithCoordinatesFromCache(): List<VideoItem> = withContext(Dispatchers.IO) {
        dao.getAllWithCoordinates().map { it.toDomain() }
    }

    private suspend fun addReverseGeocodedLocations(list: List<VideoItem>): List<VideoItem> = withContext(Dispatchers.IO) {
        val geocoder = try {
            android.location.Geocoder(context, java.util.Locale.getDefault())
        } catch (t: Throwable) {
            null
        }
        if (geocoder == null) return@withContext list
        list.map { v ->
            val lat = v.latitude
            val lon = v.longitude
            val needsGeo = lat != null && lon != null && (v.city.isNullOrBlank() || v.country.isNullOrBlank())
            if (!needsGeo) return@map v
            try {
                val results = geocoder.getFromLocation(lat!!, lon!!, 1)
                if (!results.isNullOrEmpty()) {
                    val addr = results[0]
                    val city = v.city ?: (addr.locality ?: addr.subAdminArea ?: addr.adminArea)
                    val country = v.country ?: addr.countryName
                    v.copy(city = city, country = country)
                } else v
            } catch (t: Throwable) {
                v
            }
        }
    }
}

private fun isLocaleLike(input: String): Boolean {
    val s = input.trim()
    // Matches: "en", "pt", "pt-BR", "pt-PT", etc. Also tolerates case variations.
    val localeRegex = Regex("^[a-zA-Z]{2}(-[a-zA-Z]{2,})?$")
    if (!localeRegex.matches(s)) return false
    // Heuristic: country names are rarely just 2 letters; if it's exactly 2 letters, treat as locale.
    if (s.length == 2) return true
    // If pattern is xx-YY or similar, it's a locale, not a country name.
    return s.contains('-')
}

private fun VideoEntity.toDomain(): VideoItem = VideoItem(
    videoId = videoId,
    title = title,
    channelTitle = channelTitle,
    publishedAt = publishedAt,
    thumbnailUrl = thumbnailUrl,
    tags = tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
    country = country?.takeIf { !isLocaleLike(it) },
    city = city,
    latitude = latitude,
    longitude = longitude,
    recordingDate = recordingDate
)

private fun VideoItem.toEntity(now: Long): VideoEntity = VideoEntity(
    videoId = videoId,
    title = title,
    channelTitle = channelTitle,
    publishedAt = publishedAt,
    thumbnailUrl = thumbnailUrl,
    tags = tags?.joinToString(","),
    country = country?.takeIf { !isLocaleLike(it) },
    city = city,
    latitude = latitude,
    longitude = longitude,
    recordingDate = recordingDate,
    lastUpdated = now
)