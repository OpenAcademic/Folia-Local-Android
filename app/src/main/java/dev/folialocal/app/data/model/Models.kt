package dev.folialocal.app.data.model

import kotlinx.serialization.Serializable

/**
 * 一首本地曲目。id 使用 SAF 文档 URI，保证重扫后仍能稳定对应同一文件。
 * title/artist/album 可能包含「信息覆盖」后的本地修改值；空字符串表示未知。
 */
@Serializable
data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val trackNumber: Int = 0,
    val year: Int = 0,
    /** 相对音乐根目录的文件夹路径，根目录为空字符串。 */
    val folder: String,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val lyricUri: String? = null,
    val coverUri: String? = null,
) {
    val folderName: String get() = folder.substringAfterLast('/')

    companion object {
        const val UNKNOWN_ARTIST = "Unknown Artist"
        const val UNKNOWN_ALBUM = "Unknown Album"
    }
}

@Serializable
data class LibraryIndex(
    val rootUri: String = "",
    val rootName: String = "",
    val scannedAt: Long = 0L,
    val tracks: List<Track> = emptyList(),
) {
    val isEmpty: Boolean get() = rootUri.isBlank()
}

data class AlbumGroup(
    val key: String,
    val name: String,
    val artist: String,
    val tracks: List<Track>,
) {
    val coverTrack: Track? get() = tracks.firstOrNull()
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

data class ArtistGroup(
    val name: String,
    val tracks: List<Track>,
) {
    val albumCount: Int get() = tracks.map { it.album }.distinct().size
    val coverTrack: Track? get() = tracks.firstOrNull()
}

data class FolderGroup(
    val path: String,
    val tracks: List<Track>,
) {
    val name: String get() = path.substringAfterLast('/')
}

data class LibrarySnapshot(
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val artists: List<ArtistGroup> = emptyList(),
    val folders: List<FolderGroup> = emptyList(),
    val totalDurationMs: Long = 0L,
)

fun List<Track>.toLibrarySnapshot(): LibrarySnapshot {
    val sorted = sortedWith(
        compareBy<Track> { it.title.lowercase() }
            .thenBy { it.artist.lowercase() },
    )
    val albums = sorted
        .groupBy { "${it.album}\u0000${it.artist}".lowercase() }
        .map { (key, tracks) ->
            AlbumGroup(
                key = key,
                name = tracks.first().album,
                artist = tracks.first().artist,
                tracks = tracks.sortedWith(compareBy({ it.trackNumber }, { it.title })),
            )
        }
        .sortedBy { it.name.lowercase() }
    val artists = sorted
        .groupBy { it.artist.lowercase() }
        .map { (_, tracks) -> ArtistGroup(tracks.first().artist, tracks) }
        .sortedBy { it.name.lowercase() }
    val folders = sorted
        .groupBy { it.folder }
        .map { (path, tracks) -> FolderGroup(path, tracks.sortedBy { it.title.lowercase() }) }
        .sortedBy { it.path.lowercase() }
    return LibrarySnapshot(
        tracks = sorted,
        albums = albums,
        artists = artists,
        folders = folders,
        totalDurationMs = sorted.sumOf { it.durationMs },
    )
}
