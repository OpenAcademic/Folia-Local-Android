package dev.folialocal.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.folialocal.app.R
import dev.folialocal.app.data.library.ScanProgress
import dev.folialocal.app.data.model.LibrarySnapshot
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.ui.components.EmptyState
import dev.folialocal.app.ui.components.ScreenHeader
import dev.folialocal.app.ui.components.TrackRow
import dev.folialocal.app.ui.components.formatDuration

@Composable
fun SongsScreen(
    snapshot: LibrarySnapshot,
    rootName: String,
    progress: ScanProgress,
    currentTrackId: String?,
    isPlaying: Boolean,
    accent: Color,
    onPlay: (Track, List<Track>) -> Unit,
    onSearch: () -> Unit,
    onRescan: () -> Unit,
    onEditTrack: (Track) -> Unit = {},
) {
    val titleText = stringResource(R.string.library_title)
    val localMusicText = stringResource(R.string.library_local_music)
    val countText = stringResource(R.string.songs_count, snapshot.tracks.size)
    val searchText = stringResource(R.string.search)
    val rescanText = stringResource(R.string.rescan)
    val emptyTitle = stringResource(R.string.songs_empty_title)
    val emptyBody = stringResource(R.string.songs_empty_body)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = titleText,
            subtitle = buildString {
                append(rootName.ifBlank { localMusicText })
                append(" · ")
                append(countText)
                if (snapshot.totalDurationMs > 0) {
                    append(" · ")
                    append(formatDuration(snapshot.totalDurationMs))
                }
            },
        ) {
            IconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = searchText)
            }
            IconButton(onClick = onRescan) {
                Icon(Icons.Rounded.Refresh, contentDescription = rescanText)
            }
        }
        ScanBanner(progress)
        if (snapshot.tracks.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.LibraryMusic,
                title = emptyTitle,
                body = progress.error ?: emptyBody,
                actionLabel = rescanText,
                onAction = onRescan,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(snapshot.tracks, key = { _, track -> track.id }) { _, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying,
                        accent = accent,
                        onClick = { onPlay(track, snapshot.tracks) },
                        onEdit = { onEditTrack(track) },
                    )
                }
            }
        }
    }
}

@Composable
fun ScanBanner(progress: ScanProgress) {
    if (!progress.active && progress.error == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (progress.active) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )
            Text(
                text = stringResource(R.string.scanning_progress, progress.scanned, progress.currentFolder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (progress.error != null) {
            Text(
                text = progress.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
