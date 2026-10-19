package dev.folialocal.app.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.folialocal.app.R
import dev.folialocal.app.data.lyrics.LrcMetadata
import dev.folialocal.app.data.lyrics.LyricMetadata
import dev.folialocal.app.data.library.TrackOverride
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 编辑歌曲信息：标题/艺术家/专辑保存在应用内（不改动音频文件），
 * 封面可选自定义图片；同名 .lrc 的头部标签同步写回原文件。
 */
@Composable
fun TrackEditDialog(
    track: Track,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val overrideRepo = container.trackOverrideRepository

    var title by rememberSaveable(track.id) { mutableStateOf(track.title) }
    var artist by rememberSaveable(track.id) { mutableStateOf(track.artist) }
    var album by rememberSaveable(track.id) { mutableStateOf(track.album) }
    var coverPath by rememberSaveable(track.id) { mutableStateOf(overrideRepo.get(track.id)?.coverPath) }
    var coverLoading by remember { mutableStateOf(false) }

    var lyricLoading by remember(track.id) { mutableStateOf(track.lyricUri != null) }
    var lyricTitle by rememberSaveable(track.id) { mutableStateOf("") }
    var lyricArtist by rememberSaveable(track.id) { mutableStateOf("") }
    var lyricAlbum by rememberSaveable(track.id) { mutableStateOf("") }
    var lyricist by rememberSaveable(track.id) { mutableStateOf("") }
    var offset by rememberSaveable(track.id) { mutableStateOf("0") }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val noWritePermissionError = stringResource(R.string.error_no_write_permission)
    val saveFailedError = stringResource(R.string.error_save_failed)

    LaunchedEffect(track.id) {
        if (track.lyricUri == null) return@LaunchedEffect
        val metadata = container.lyricsRepository.readRaw(track)?.let(LrcMetadata::parse) ?: LyricMetadata()
        lyricTitle = metadata.title
        lyricArtist = metadata.artist
        lyricAlbum = metadata.album
        lyricist = metadata.lyricist
        offset = metadata.offsetMs.toString()
        lyricLoading = false
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                coverLoading = true
                coverPath = overrideRepo.importCover(track.id, uri)
                coverLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving && !coverLoading) onDismiss() },
        title = { Text(stringResource(R.string.track_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.track_edit_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CoverPreview(coverPath = coverPath, accent = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            enabled = !saving && !coverLoading,
                            onClick = { coverPicker.launch(arrayOf("image/*")) },
                        ) {
                            Text(stringResource(R.string.track_edit_pick_cover))
                        }
                        if (coverPath != null) {
                            TextButton(
                                enabled = !saving && !coverLoading,
                                onClick = { coverPath = null },
                            ) {
                                Text(stringResource(R.string.track_edit_remove_cover))
                            }
                        }
                        if (coverLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.field_title)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(R.string.field_artist)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text(stringResource(R.string.field_album)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (track.lyricUri != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(R.string.lyric_tags_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (lyricLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        OutlinedTextField(
                            value = lyricTitle,
                            onValueChange = { lyricTitle = it },
                            label = { Text(stringResource(R.string.field_lyric_title)) },
                            singleLine = true,
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = lyricArtist,
                            onValueChange = { lyricArtist = it },
                            label = { Text(stringResource(R.string.field_lyric_artist)) },
                            singleLine = true,
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = lyricAlbum,
                            onValueChange = { lyricAlbum = it },
                            label = { Text(stringResource(R.string.field_lyric_album)) },
                            singleLine = true,
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = lyricist,
                            onValueChange = { lyricist = it },
                            label = { Text(stringResource(R.string.field_lyric_writer)) },
                            singleLine = true,
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = offset,
                            onValueChange = { offset = it.filter { char -> char.isDigit() || char == '-' } },
                            label = { Text(stringResource(R.string.field_lyric_offset)) },
                            singleLine = true,
                            enabled = !saving,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loadingAny(loading = saving, cover = coverLoading, lyric = lyricLoading),
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        // 1) 曲目信息覆盖（应用内保存，不改音频文件）
                        overrideRepo.set(
                            track.id,
                            TrackOverride(
                                title = title.trim().ifBlank { null },
                                artist = artist.trim().ifBlank { null },
                                album = album.trim().ifBlank { null },
                                coverPath = coverPath,
                            ),
                        )
                        container.libraryRepository.refreshOverrides()
                        container.coverRepository.clear()
                        // 2) 歌词头部标签写回 .lrc 文件
                        if (track.lyricUri != null && !lyricLoading) {
                            if (!container.libraryRepository.ensureWritePermission()) {
                                error = noWritePermissionError
                                saving = false
                                return@launch
                            }
                            val result = container.lyricsRepository.writeMetadata(
                                track = track,
                                metadata = LyricMetadata(
                                    title = lyricTitle,
                                    artist = lyricArtist,
                                    album = lyricAlbum,
                                    lyricist = lyricist,
                                    offsetMs = offset.toLongOrNull() ?: 0L,
                                ),
                            )
                            result.fold(
                                onSuccess = {
                                    saving = false
                                    onSaved()
                                    onDismiss()
                                },
                                onFailure = {
                                    error = it.message ?: saveFailedError
                                    saving = false
                                },
                            )
                        } else {
                            saving = false
                            onSaved()
                            onDismiss()
                        }
                    }
                },
            ) {
                Text(if (saving) stringResource(R.string.saving) else stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving && !coverLoading) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun loadingAny(loading: Boolean, cover: Boolean, lyric: Boolean): Boolean = loading || cover || lyric

@Composable
private fun CoverPreview(coverPath: String?, accent: Color) {
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = coverPath,
        producer = {
            value = withContext(Dispatchers.IO) {
                coverPath?.let { path ->
                    runCatching {
                        BitmapFactory.decodeFile(File(path).absolutePath)
                    }.getOrNull()
                }
            }
        },
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(shape)
            .background(accent.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                tint = accent.copy(alpha = 0.65f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
