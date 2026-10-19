package dev.folialocal.app.data.library

import android.content.Context
import dev.folialocal.app.data.model.LibraryIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 曲库索引持久化。索引是纯本地 JSON，删除应用数据即可彻底清除。
 */
class LibraryIndexStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val indexFile get() = File(context.filesDir, "library_index.json")

    fun load(): LibraryIndex {
        if (!indexFile.exists()) return LibraryIndex()
        return runCatching {
            json.decodeFromString<LibraryIndex>(indexFile.readText())
        }.getOrElse { LibraryIndex() }
    }

    suspend fun save(index: LibraryIndex) = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(context.filesDir, "library_index.json.tmp")
            temp.writeText(json.encodeToString(index))
            if (!temp.renameTo(indexFile)) {
                temp.copyTo(indexFile, overwrite = true)
                temp.delete()
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        indexFile.delete()
    }
}
