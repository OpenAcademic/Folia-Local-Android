package dev.folialocal.app.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.ui.theme.DefaultAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * 封面加载：优先同目录封面，其次音频内嵌封面，最后返回 null 由 UI 画渐变占位。
 * 只在内存里缓存，不做网络请求。
 */
class CoverRepository(private val context: Context) {

    private val memoryCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val missing = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val paletteCache = ConcurrentHashMap<String, Int>()

    suspend fun load(track: Track, targetPx: Int = 900): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${track.id}@$targetPx"
        memoryCache.get(cacheKey)?.let { return@withContext it }
        if (cacheKey in missing) return@withContext null

        val bitmap = track.coverUri?.let { uri ->
            decodeSampled(Uri.parse(uri), targetPx)
        } ?: embeddedArtwork(track, targetPx)

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        } else {
            missing += cacheKey
        }
        bitmap
    }

    suspend fun accentColor(track: Track): Int = withContext(Dispatchers.Default) {
        val cacheKey = "accent@${track.id}"
        paletteCache[cacheKey]?.let { return@withContext it }
        val bitmap = load(track, 320) ?: return@withContext DefaultAccent
        val palette = Palette.from(bitmap).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.dominantSwatch
            ?: palette.mutedSwatch
        val color = swatch?.rgb ?: DefaultAccent
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        val sanitized = if (hsv[1] < 0.16f || hsv[2] < 0.30f) {
            DefaultAccent
        } else {
            hsv[1] = hsv[1].coerceAtLeast(0.42f)
            hsv[2] = hsv[2].coerceIn(0.72f, 0.94f)
            android.graphics.Color.HSVToColor(hsv)
        }
        paletteCache[cacheKey] = sanitized
        sanitized
    }

    fun clear() {
        memoryCache.evictAll()
        missing.clear()
        paletteCache.clear()
    }

    private fun embeddedArtwork(track: Track, targetPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            runCatching { retriever.setDataSource(context, Uri.parse(track.uri)) }
                .recoverCatching {
                    // 某些 SAF provider 不接受 context+uri，改用文件描述符。
                    context.contentResolver.openFileDescriptor(Uri.parse(track.uri), "r")?.use { descriptor ->
                        retriever.setDataSource(descriptor.fileDescriptor)
                    } ?: throw it
                }
            val bytes = retriever.embeddedPicture ?: return null
            decodeSampled(bytes, targetPx)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeSampled(uri: Uri, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }
        return decodeWithImageDecoder(uri, targetPx)
    }

    /**
     * BitmapFactory 对 HEIF/AVIF 等格式可能返回 null，API 28+ 用 ImageDecoder 再试一次。
     */
    private fun decodeWithImageDecoder(uri: Uri, targetPx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                var sample = 1
                while (longest / (sample * 2) >= targetPx) sample *= 2
                decoder.setTargetSampleSize(sample)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
    }

    private fun decodeSampled(bytes: ByteArray, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= target) {
            sample *= 2
            longest /= 2
        }
        return sample
    }
}
