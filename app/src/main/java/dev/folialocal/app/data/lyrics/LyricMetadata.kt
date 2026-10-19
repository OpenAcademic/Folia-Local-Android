package dev.folialocal.app.data.lyrics

/**
 * LRC 文件头部的可编辑元数据。歌词正文与未知标签在保存时原样保留。
 */
data class LyricMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val lyricist: String = "",
    val offsetMs: Long = 0L,
)

object LrcMetadata {

    private val tagRegex = Regex("""^\s*\[(ti|ar|al|by|offset)\s*:\s*(.*?)]\s*$""", RegexOption.IGNORE_CASE)
    private val metadataLineRegex = Regex("""^\s*\[(ti|ar|al|by|offset)\s*:.*?]\s*$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): LyricMetadata {
        var title = ""
        var artist = ""
        var album = ""
        var lyricist = ""
        var offset = 0L
        raw.lineSequence().forEach { line ->
            val match = tagRegex.matchEntire(line) ?: return@forEach
            val value = match.groupValues[2].trim()
            when (match.groupValues[1].lowercase()) {
                "ti" -> title = value
                "ar" -> artist = value
                "al" -> album = value
                "by" -> lyricist = value
                "offset" -> offset = value.toLongOrNull() ?: 0L
            }
        }
        return LyricMetadata(title, artist, album, lyricist, offset)
    }

    /**
     * 删除旧的头部标签后重新写入。其余行（包括歌词、翻译、未知标签）保持原顺序。
     */
    fun apply(raw: String, metadata: LyricMetadata): String {
        val body = raw.replace("\r\n", "\n").replace('\r', '\n')
            .lineSequence()
            .filterNot { metadataLineRegex.matches(it) }
            .toList()
        val header = buildList {
            metadata.title.cleanTag().takeIf { it.isNotEmpty() }?.let { add("[ti:$it]") }
            metadata.artist.cleanTag().takeIf { it.isNotEmpty() }?.let { add("[ar:$it]") }
            metadata.album.cleanTag().takeIf { it.isNotEmpty() }?.let { add("[al:$it]") }
            metadata.lyricist.cleanTag().takeIf { it.isNotEmpty() }?.let { add("[by:$it]") }
            if (metadata.offsetMs != 0L) {
                add("[offset:${metadata.offsetMs.coerceIn(-10_000L, 10_000L)}]")
            }
        }
        return (header + body).joinToString("\n").trimEnd() + "\n"
    }

    private fun String.cleanTag(): String = trim().replace("]", "").replace("[", "")
}
