package dev.folialocal.app.data.lyrics

data class LyricSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    /** 逐字/逐词时间轴；空表示只有行级时间。 */
    val segments: List<LyricSegment> = emptyList(),
)

data class Lyrics(
    val lines: List<LyricLine>,
    val hasWordTiming: Boolean,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    fun lineIndexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].startMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
