package dev.folialocal.app.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dev.folialocal.app.data.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 曲目信息覆盖。音频文件本身不会被改动；标题/艺术家/专辑/封面的修改
 * 保存在应用私有目录，重新扫描后仍然生效。
 */
@Serializable
data class TrackOverride(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** 应用私有目录里的自定义封面文件路径。 */
    val coverPath: String? = null,
) {
    val hasContent: Boolean
        get() = !title.isNullOrBlank() || !artist.isNullOrBlank() || !album.isNullOrBlank() || !coverPath.isNullOrBlank()
}

class TrackOverrideRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "track_overrides.json")
    private val overrides: MutableMap<String, TrackOverride> = load()

    fun get(trackId: String): TrackOverride? = overrides[trackId]

    fun set(trackId: String, override: TrackOverride) {
        if (override.hasContent) {
            overrides[trackId] = override
        } else {
            overrides.remove(trackId)
        }
        persist()
    }

    /** 把覆盖信息合并进扫描出的曲目列表。 */
    fun applyTo(tracks: List<Track>): List<Track> = tracks.map { track ->
        val override = overrides[track.id] ?: return@map track
        track.copy(
            title = override.title?.takeIf { it.isNotBlank() } ?: track.title,
            artist = override.artist?.takeIf { it.isNotBlank() } ?: track.artist,
            album = override.album?.takeIf { it.isNotBlank() } ?: track.album,
            coverUri = override.coverPath?.let { Uri.fromFile(File(it)).toString() } ?: track.coverUri,
        )
    }

    /** 把用户挑选的图片缩放后复制到应用私有目录，返回封面文件路径。 */
    suspend fun importCover(trackId: String, source: Uri): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val target = File(dir, "cover_${Integer.toHexString(trackId.hashCode())}.jpg")
            val bitmap = decodeSampled(source) ?: error(context.getString(dev.folialocal.app.R.string.error_cover_read))
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            target.absolutePath
        }.getOrNull()
    }

    private fun decodeSampled(source: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return runCatching {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, source),
                ) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    if (info.size.width > 1600) decoder.setTargetSampleSize(2)
                }
            }.getOrNull()
        }
        return runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeStream(input, null, options)
            }
        }.getOrNull()
    }

    private fun load(): MutableMap<String, TrackOverride> = runCatching {
        if (!file.exists()) return@runCatching mutableMapOf()
        val raw = file.readText()
        if (raw.isBlank()) {
            mutableMapOf()
        } else {
            json.decodeFromString<Map<String, TrackOverride>>(raw).toMutableMap()
        }
    }.getOrDefault(mutableMapOf())

    private fun persist() {
        runCatching {
            file.writeText(json.encodeToString(overrides.toMap()))
        }
    }
}
