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

    private const val BASE_URL = "https://www.googleapis.com/youtube/v3/search"

    private val CHANNEL_IDS = listOf(
        "UCynoa1DjwnvHAowA_jiMEAQ",
        "UCK0KOjX3beyB9nzonls0cuw",
        "UCACkIrvrGAQ7kuc0hMVwvmA",
        "UCtWRAKKvOEA0CXOue9BG8ZA"
    )

    fun fetchLatestVideos(maxPerChannel: Int = 10): List<VideoItem> {
        val key = BuildConfig.YOUTUBE_API_KEY
        if (key.isBlank()) {
            Log.w("YouTubeApi", "Missing YOUTUBE_API_KEY; returning empty list")
            return emptyList()
        }
        val all = mutableListOf<VideoItem>()
        for (channelId in CHANNEL_IDS) {
            try {
                val items = fetchChannelVideos(channelId, key, maxPerChannel)
                all.addAll(items)
            } catch (t: Throwable) {
                Log.e("YouTubeApi", "Failed to fetch for channel $channelId", t)
            }
        }
        // Sort by publishedAt desc
        return all.sortedByDescending { it.publishedAt }
    }

    private fun fetchChannelVideos(channelId: String, apiKey: String, maxResults: Int): List<VideoItem> {
        val params = mapOf(
            "part" to "snippet",
            "channelId" to channelId,
            "maxResults" to maxResults.toString(),
            "order" to "date",
            "type" to "video",
            "key" to apiKey
        )
        val query = params.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
        val url = URL("$BASE_URL?$query")
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
        val itemsArray = json.optJSONArray("items") ?: return emptyList()
        val result = mutableListOf<VideoItem>()
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
                result.add(
                    VideoItem(
                        videoId = videoId,
                        title = title,
                        channelTitle = channelTitle,
                        publishedAt = publishedAt,
                        thumbnailUrl = thumbUrl
                    )
                )
            }
        }
        return result
    }
}
