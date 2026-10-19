package dev.folialocal.app.data.lyrics

/**
 * 解析 LRC / 增强型逐字 LRC。
 *
 * 支持：
 * - 一行多个时间标签；
 * - `[mm:ss]`、`[mm:ss.xx]`、`[mm:ss.xxx]`；
 * - `[offset:±ms]`；
 * - `<mm:ss.xx>` 逐字/逐词时间轴。
 */
object LrcParser {

    private val lineTimeRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTimeRegex = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val offsetRegex = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private const val LAST_LINE_GUESS_MS = 4_000L

    fun parse(raw: String, fallbackDurationMs: Long = 0L): Lyrics {
        val offsetMs = offsetRegex.find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val candidates = mutableListOf<Pair<Long, String>>()

        raw.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { line ->
            val matches = lineTimeRegex.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val content = line.substring(matches.last().range.last + 1).trim()
            if (content.isEmpty()) return@forEach
            matches.forEach { match ->
                val rawStart = toMillis(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                candidates += rawStart to content
            }
        }

        val ordered = candidates
            .distinctBy { it.first to it.second }
            .sortedBy { it.first }
        if (ordered.isEmpty()) return Lyrics(emptyList(), false)

        val adjusted = ordered.map { (rawStart, text) ->
            (rawStart - offsetMs).coerceAtLeast(0L) to text
        }
        var hasWordTiming = false

        val lines = adjusted.mapIndexed { index, (startMs, text) ->
            val nextStart = adjusted.getOrNull(index + 1)?.first
            val endMs = nextStart?.takeIf { it > startMs }
                ?: run {
                    val candidate = if (fallbackDurationMs > startMs) fallbackDurationMs else startMs + LAST_LINE_GUESS_MS
                    candidate
                }
            val segments = parseSegments(text, startMs, endMs, offsetMs)
            if (segments.isNotEmpty()) hasWordTiming = true
            LyricLine(
                startMs = startMs,
                endMs = endMs,
                text = text.replace(wordTimeRegex, "").trim(),
                segments = segments,
            )
        }.filter { it.text.isNotEmpty() || it.segments.isNotEmpty() }

        return Lyrics(lines, hasWordTiming)
    }

    private fun parseSegments(text: String, lineStart: Long, lineEnd: Long, offsetMs: Long): List<LyricSegment> {
        val matches = wordTimeRegex.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        val segments = mutableListOf<LyricSegment>()
        val firstStart = adjust(toMillis(matches.first()), offsetMs).coerceIn(lineStart, lineEnd)
        val prefix = text.substring(0, matches.first().range.first).trim()
        if (prefix.isNotEmpty() && firstStart > lineStart) {
            segments += LyricSegment(prefix, lineStart, firstStart)
        }

        matches.forEachIndexed { index, match ->
            val rawStart = toMillis(match)
            val nextRawStart = matches.getOrNull(index + 1)?.let(::toMillis)
            val start = adjust(rawStart, offsetMs).coerceIn(lineStart, lineEnd)
            val end = (nextRawStart?.let { adjust(it, offsetMs) } ?: lineEnd).coerceIn(start, lineEnd)
            val endOfThisTag = match.range.last + 1
            val endOfText = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val segmentText = text.substring(endOfThisTag, endOfText).trim()
            if (segmentText.isNotEmpty() && end > start) {
                segments += LyricSegment(segmentText, start, end)
            }
        }
        return segments.sortedBy { it.startMs }
    }

    private fun toMillis(match: MatchResult): Long = toMillis(
        match.groupValues[1],
        match.groupValues[2],
        match.groupValues.getOrNull(3).orEmpty(),
    )

    private fun toMillis(minutes: String, seconds: String, fraction: String): Long {
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100) ?: 0L
            2 -> fraction.toLongOrNull()?.times(10) ?: 0L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return (minutes.toLongOrNull() ?: 0L) * 60_000L +
            (seconds.toLongOrNull() ?: 0L) * 1_000L +
            fractionMs
    }

    private fun adjust(rawMs: Long, offsetMs: Long): Long = (rawMs - offsetMs).coerceAtLeast(0L)
}
