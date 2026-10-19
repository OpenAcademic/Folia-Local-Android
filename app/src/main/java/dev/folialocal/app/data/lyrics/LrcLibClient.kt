package dev.folialocal.app.data.lyrics

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** 一条在线歌词搜索结果。 */
data class OnlineLyrics(
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationMs: Long,
    val syncedLyrics: String?,
    val plainLyrics: String?,
) {
    val hasSynced: Boolean get() = !syncedLyrics.isNullOrBlank()
}

/**
 * lrclib.net 公共歌词库客户端。只在用户手动搜索/下载歌词时联网。
 * 接口：https://lrclib.net/docs （免费、无需密钥）。
 */
class LrcLibClient(
    private val userAgent: String = "FoliaLocal/1.0 (https://github.com/chthollyphile/folia-major)",
) {

    fun search(trackName: String, artistName: String): List<OnlineLyrics> {
        val name = trackName.trim()
        val artist = artistName.trim()
        val attempts = buildList {
            if (name.isNotBlank() && artist.isNotBlank()) {
                add("https://lrclib.net/api/search?track_name=${enc(name)}&artist_name=${enc(artist)}")
            }
            if (name.isNotBlank()) {
                val query = if (artist.isNotBlank()) "$artist $name" else name
                add("https://lrclib.net/api/search?q=${enc(query)}")
            }
        }
        attempts.forEach { url ->
            val results = runCatching { fetch(url) }.getOrDefault(emptyList())
            if (results.isNotEmpty()) return results
        }
        return emptyList()
    }

    private fun fetch(url: String): List<OnlineLyrics> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) error("lrclib.net 响应异常（HTTP ${connection.responseCode}）")
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): List<OnlineLyrics> {
        val array = JSONArray(body)
        val items = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            OnlineLyrics(
                trackName = item.optString("trackName"),
                artistName = item.optString("artistName"),
                albumName = item.optString("albumName"),
                durationMs = (item.optDouble("duration") * 1000).toLong(),
                syncedLyrics = if (item.isNull("syncedLyrics")) null else item.optString("syncedLyrics"),
                plainLyrics = if (item.isNull("plainLyrics")) null else item.optString("plainLyrics"),
            )
        }.filter { it.trackName.isNotBlank() }
        return items
            .distinctBy { "${it.trackName}|${it.artistName}|${it.albumName}|${it.durationMs}" }
            .sortedWith(
                compareByDescending<OnlineLyrics> { it.hasSynced }
                    .thenBy { it.artistName.length },
            )
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
