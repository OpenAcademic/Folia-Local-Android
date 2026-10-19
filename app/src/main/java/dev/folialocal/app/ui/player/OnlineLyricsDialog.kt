package dev.folialocal.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.folialocal.app.R
import dev.folialocal.app.data.lyrics.OnlineLyrics
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.ui.LocalAppContainer
import dev.folialocal.app.ui.components.formatDuration
import kotlinx.coroutines.launch

/**
 * 在线歌词下载：搜索 lrclib.net，把选中的逐行歌词保存为与音频同名的 .lrc 文件。
 */
@Composable
fun OnlineLyricsDialog(
    track: Track,
    onDismiss: () -> Unit,
    onImported: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(track.id) { mutableStateOf(track.title) }
    var artist by rememberSaveable(track.id) { mutableStateOf(track.artist) }
    var searching by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<OnlineLyrics>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val downloadFailedError = stringResource(R.string.online_lyrics_download_failed)

    fun search() {
        scope.launch {
            searching = true
            error = null
            results = container.onlineLyricsService.search(title, artist)
            searching = false
        }
    }

    LaunchedEffect(track.id) { search() }

    fun download(item: OnlineLyrics) {
        val text = item.syncedLyrics ?: return
        scope.launch {
            saving = true
            error = null
            val result = container.onlineLyricsService.save(track, text)
            saving = false
            result.fold(
                onSuccess = {
                    onImported()
                    onDismiss()
                },
                onFailure = { error = it.message ?: downloadFailedError },
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving && !searching) onDismiss() },
        title = { Text(stringResource(R.string.online_lyrics_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.field_title)) },
                        singleLine = true,
                        enabled = !searching && !saving,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text(stringResource(R.string.field_artist)) },
                        singleLine = true,
                        enabled = !searching && !saving,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = { search() },
                    enabled = !searching && !saving && title.isNotBlank(),
                ) {
                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.search))
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val list = results
                when {
                    list == null -> {}
                    list.isEmpty() -> Text(
                        text = stringResource(R.string.online_lyrics_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> list.forEach { item ->
                        OnlineLyricsRow(
                            item = item,
                            enabled = !saving && item.hasSynced,
                            onClick = { download(item) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.online_lyrics_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !saving && !searching) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun OnlineLyricsRow(
    item: OnlineLyrics,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.trackName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(item.artistName, item.albumName)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (item.hasSynced) R.string.online_lyrics_synced_badge else R.string.online_lyrics_plain_badge,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (item.hasSynced) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatDuration(item.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
