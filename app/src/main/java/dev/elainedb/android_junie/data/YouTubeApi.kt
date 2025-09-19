package dev.elainedb.android_junie.data

import android.util.Log
import dev.elainedb.android_junie.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object YouTubeApi {

    private const val SEARCH_URL = "https://www.googleapis.com/youtube/v3/search"
    private const val VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos"

    val CHANNEL_IDS = listOf(
        "UCynoa1DjwnvHAowA_jiMEAQ",
        "UCK0KOjX3beyB9nzonls0cuw",
        "UCACkIrvrGAQ7kuc0hMVwvmA",
        "UCtWRAKKvOEA0CXOue9BG8ZA"
    )

    fun fetchAllVideos(): List<VideoItem> {
        val key = BuildConfig.YOUTUBE_API_KEY
        if (key.isBlank()) {
            Log.w("YouTubeApi", "Missing YOUTUBE_API_KEY; returning empty list")
            return emptyList()
        }
        val all = mutableListOf<VideoItem>()
        for (channelId in CHANNEL_IDS) {
            try {
                val idsAndSnippets = fetchChannelVideoIdsPaged(channelId, key)
                val detailed = fetchVideoDetailsBatched(idsAndSnippets.map { it.first }, key)
                // Merge details with basic snippet fields
                idsAndSnippets.forEach { (id, basic) ->
                    val d = detailed[id]
                    all.add(
                        VideoItem(
                            videoId = id,
                            title = basic.title,
                            channelTitle = basic.channelTitle,
                            publishedAt = basic.publishedAt,
                            thumbnailUrl = basic.thumbnailUrl,
                            tags = d?.tags,
                            country = d?.country,
                            city = d?.city,
                            latitude = d?.latitude,
                            longitude = d?.longitude,
                            recordingDate = d?.recordingDate
                        )
                    )
                }
            } catch (t: Throwable) {
                Log.e("YouTubeApi", "Failed to fetch for channel $channelId", t)
            }
        }
        // Sort by publishedAt desc
        return all.sortedByDescending { it.publishedAt }
    }

    private data class BasicSnippet(
        val title: String,
        val channelTitle: String,
        val publishedAt: String,
        val thumbnailUrl: String
    )

    private data class Detail(
        val tags: List<String>?,
        val country: String?,
        val city: String?,
        val latitude: Double?,
        val longitude: Double?,
        val recordingDate: String?
    )

    private fun fetchChannelVideoIdsPaged(channelId: String, apiKey: String): List<Pair<String, BasicSnippet>> {
        val all = mutableListOf<Pair<String, BasicSnippet>>()
        var pageToken: String? = null
        do {
            val params = mutableMapOf(
                "part" to "snippet",
                "channelId" to channelId,
                "maxResults" to "50",
                "order" to "date",
                "type" to "video",
                "key" to apiKey
            )
            if (!pageToken.isNullOrBlank()) params["pageToken"] = pageToken!!
            val query = params.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
            val url = URL("$SEARCH_URL?$query")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "android-junie/1.0")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw java.io.IOException("YouTube API HTTP $code for channel=$channelId: ${response.take(300)}")
            }
            val json = JSONObject(response)
            val itemsArray = json.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.getJSONObject(i)
                    val idObj = item.optJSONObject("id")
                    val videoId = idObj?.optString("videoId").orEmpty()
                    val snippet = item.optJSONObject("snippet")
                    val title = snippet?.optString("title").orEmpty()
                    val channelTitle = snippet?.optString("channelTitle").orEmpty()
                    val publishedAt = snippet?.optString("publishedAt").orEmpty()
                    val thumbnails = snippet?.optJSONObject("thumbnails")
                    val thumbUrl = thumbnails?.optJSONObject("medium")?.optString("url")
                        ?: thumbnails?.optJSONObject("default")?.optString("url")
                        ?: ""
                    if (videoId.isNotBlank()) {
                        all.add(videoId to BasicSnippet(title, channelTitle, publishedAt, thumbUrl))
                    }
                }
            }
            pageToken = json.optString("nextPageToken")?.ifBlank { null }
        } while (!pageToken.isNullOrBlank())
        return all
    }

    private fun fetchVideoDetailsBatched(videoIds: List<String>, apiKey: String): Map<String, Detail> {
        if (videoIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Detail>()
        val chunkSize = 50
        var i = 0
        while (i < videoIds.size) {
            val chunk = videoIds.subList(i, kotlin.math.min(i + chunkSize, videoIds.size))
            result.putAll(fetchVideoDetails(chunk, apiKey))
            i += chunkSize
        }
        return result
    }

    private fun fetchVideoDetails(videoIds: List<String>, apiKey: String): Map<String, Detail> {
        if (videoIds.isEmpty()) return emptyMap()
        val idsParam = videoIds.joinToString(",")
        val params = mapOf(
            "part" to "snippet,recordingDetails",
            "id" to idsParam,
            "key" to apiKey
        )
        val query = params.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
        val url = URL("$VIDEOS_URL?$query")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "android-junie/1.0")
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw java.io.IOException("YouTube videos API HTTP $code: ${response.take(300)}")
        }
        val result = mutableMapOf<String, Detail>()
        val json = JSONObject(response)
        val items = json.optJSONArray("items")
        if (items != null) {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val id = item.optString("id")
                val snippet = item.optJSONObject("snippet")
                val tagsArray = snippet?.optJSONArray("tags")
                val tags = mutableListOf<String>()
                if (tagsArray != null) {
                    for (t in 0 until tagsArray.length()) {
                        tags.add(tagsArray.optString(t))
                    }
                }
                val rec = item.optJSONObject("recordingDetails")
                val loc = rec?.optJSONObject("location")
                val lat = loc?.optDouble("latitude")
                val lon = loc?.optDouble("longitude")
                val recDate = rec?.optString("recordingDate")
                // We do NOT use defaultAudioLanguage or any locale fields for country; rely on GPS + reverse geocoding only.
                val country: String? = null
                val city: String? = null
                result[id] = Detail(
                    tags = if (tags.isEmpty()) null else tags,
                    country = country,
                    city = city,
                    latitude = if (lat?.isNaN() == true) null else lat,
                    longitude = if (lon?.isNaN() == true) null else lon,
                    recordingDate = recDate
                )
            }
        }
        return result
    }
}
