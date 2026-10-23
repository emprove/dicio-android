package org.stypox.dicio.skills.music_search

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Official YouTube Data API v3, search.list:
 * https://developers.google.com/youtube/v3/docs/search/list
 * Each call costs 100 quota units; the free daily quota is 10,000 units (~100 searches/day).
 */
object YouTubeSearch {
    private const val ENDPOINT = "https://www.googleapis.com/youtube/v3/search"

    /** @return (videoId, title) of the best match, or null if nothing was found */
    fun findVideo(apiKey: String, query: String): Pair<String, String>? {
        val url = ENDPOINT +
                "?part=snippet&type=video&maxResults=1" +
                "&videoCategoryId=10" + // Music
                "&q=" + URLEncoder.encode(query, "UTF-8") +
                "&key=" + URLEncoder.encode(apiKey, "UTF-8")

        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(text).getJSONObject("error").getString("message")
                }.getOrNull()
                throw IllegalStateException("HTTP $code" + (message?.let { ": $it" } ?: ""))
            }

            val items = JSONObject(text).optJSONArray("items") ?: return null
            if (items.length() == 0) return null
            val item = items.getJSONObject(0)
            val videoId = item.getJSONObject("id").optString("videoId")
            if (videoId.isEmpty()) return null
            val title = item.optJSONObject("snippet")?.optString("title")
                ?.replace("&quot;", "\"")?.replace("&#39;", "'")?.replace("&amp;", "&")
                .orEmpty().ifEmpty { query }
            return Pair(videoId, title)
        } finally {
            connection.disconnect()
        }
    }
}
