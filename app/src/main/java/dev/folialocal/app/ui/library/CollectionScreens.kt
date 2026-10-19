package dev.folialocal.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.folialocal.app.R
import dev.folialocal.app.data.model.AlbumGroup
import dev.folialocal.app.data.model.ArtistGroup
import dev.folialocal.app.data.model.FolderGroup
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.ui.components.CoverArt
import dev.folialocal.app.ui.components.EmptyState
import dev.folialocal.app.ui.components.ScreenHeader
import dev.folialocal.app.ui.components.TrackRow
import dev.folialocal.app.ui.components.artistLabel
import dev.folialocal.app.ui.components.formatDuration
import dev.folialocal.app.ui.components.nameLabel

@Composable
fun AlbumsScreen(
    albums: List<AlbumGroup>,
    onOpenAlbum: (AlbumGroup) -> Unit,
) {
    val titleText = stringResource(R.string.albums_title)
    val countText = stringResource(R.string.albums_count, albums.size)
    val emptyTitle = stringResource(R.string.albums_empty_title)
    val emptyBody = stringResource(R.string.albums_empty_body)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = titleText, subtitle = countText)
        if (albums.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Album,
                title = emptyTitle,
                body = emptyBody,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(albums, key = { it.key }) { album ->
                    AlbumCard(album = album, onClick = { onOpenAlbum(album) })
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumGroup, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        CoverArt(
            track = album.coverTrack,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            corner = 14.dp,
            targetPx = 520,
        )
        Text(
            text = album.nameLabel(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artistLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AlbumDetailScreen(
    album: AlbumGroup?,
    currentTrackId: String?,
    isPlaying: Boolean,
    accent: Color,
    onBack: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onEditTrack: (Track) -> Unit = {},
) {
    val missingTitle = stringResource(R.string.album_missing)
    val missingBody = stringResource(R.string.album_missing_body)
    val titleText = stringResource(R.string.albums_title)
    val backLabel = stringResource(R.string.back)
    val playAllLabel = stringResource(R.string.play_all)
    val trackAndDuration = stringResource(R.string.track_and_duration, album?.tracks?.size ?: 0, formatDuration(album?.durationMs ?: 0L))
    if (album == null) {
        EmptyState(
            icon = Icons.Rounded.Album,
            title = missingTitle,
            body = missingBody,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            ScreenHeader(
                title = titleText,
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel)
                    }
                },
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(
                    track = album.coverTrack,
                    modifier = Modifier.size(128.dp),
                    corner = 16.dp,
                    targetPx = 520,
                )
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = album.nameLabel(),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = album.artistLabel(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = trackAndDuration,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { onPlay(album.tracks.first(), album.tracks) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(playAllLabel)
                    }
                }
            }
        }
        itemsIndexed(album.tracks, key = { _, track -> track.id }) { _, track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                accent = accent,
                onClick = { onPlay(track, album.tracks) },
                onEdit = { onEditTrack(track) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
fun ArtistsScreen(
    artists: List<ArtistGroup>,
    onOpenArtist: (ArtistGroup) -> Unit,
) {
    val titleText = stringResource(R.string.artists_title)
    val countText = stringResource(R.string.artists_count, artists.size)
    val emptyTitle = stringResource(R.string.artists_empty_title)
    val emptyBody = stringResource(R.string.artists_empty_body)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = titleText, subtitle = countText)
        if (artists.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Person,
                title = emptyTitle,
                body = emptyBody,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(artists, key = { it.name }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenArtist(artist) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverArt(
                            track = artist.coverTrack,
                            modifier = Modifier.size(52.dp),
                            corner = 26.dp,
                            targetPx = 260,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = artist.nameLabel(),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.albums_of_artist, artist.albumCount, artist.tracks.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artist: ArtistGroup?,
    currentTrackId: String?,
    isPlaying: Boolean,
    accent: Color,
    onBack: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onEditTrack: (Track) -> Unit = {},
) {
    val missingTitle = stringResource(R.string.artist_missing)
    val missingBody = stringResource(R.string.album_missing_body)
    val titleText = stringResource(R.string.artists_title)
    val backLabel = stringResource(R.string.back)
    val playAllLabel = stringResource(R.string.play_all)
    if (artist == null) {
        EmptyState(
            icon = Icons.Rounded.Person,
            title = missingTitle,
            body = missingBody,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            ScreenHeader(
                title = titleText,
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel)
                    }
                },
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoverArt(
                    track = artist.coverTrack,
                    modifier = Modifier.size(120.dp),
                    corner = 60.dp,
                    targetPx = 480,
                )
                Text(
                    text = artist.nameLabel(),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.albums_of_artist, artist.albumCount, artist.tracks.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { onPlay(artist.tracks.first(), artist.tracks) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(playAllLabel)
                }
            }
        }
        itemsIndexed(artist.tracks, key = { _, track -> track.id }) { _, track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                accent = accent,
                onClick = { onPlay(track, artist.tracks) },
                onEdit = { onEditTrack(track) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
fun FoldersScreen(
    folders: List<FolderGroup>,
    onOpenFolder: (FolderGroup) -> Unit,
) {
    val titleText = stringResource(R.string.folders_title)
    val countText = stringResource(R.string.folders_count, folders.size)
    val emptyTitle = stringResource(R.string.folders_empty_title)
    val emptyBody = stringResource(R.string.folders_empty_body)
    val rootFolderText = stringResource(R.string.root_folder)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = titleText, subtitle = countText)
        if (folders.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Folder,
                title = emptyTitle,
                body = emptyBody,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(folders, key = { it.path }) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenFolder(folder) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = folder.path.ifBlank { rootFolderText },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = stringResource(R.string.tracks_of, folder.tracks.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderDetailScreen(
    folder: FolderGroup?,
    currentTrackId: String?,
    isPlaying: Boolean,
    accent: Color,
    onBack: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onEditTrack: (Track) -> Unit = {},
) {
    val missingTitle = stringResource(R.string.folder_missing)
    val missingBody = stringResource(R.string.album_missing_body)
    val rootFolderText = stringResource(R.string.root_folder)
    val backLabel = stringResource(R.string.back)
    val playAllLabel = stringResource(R.string.play_all)
    if (folder == null) {
        EmptyState(
            icon = Icons.Rounded.Folder,
            title = missingTitle,
            body = missingBody,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            ScreenHeader(
                title = folder.name,
                subtitle = "${folder.path.ifBlank { rootFolderText }} · ${stringResource(R.string.tracks_of, folder.tracks.size)}",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel)
                    }
                },
            ) {
                IconButton(onClick = { onPlay(folder.tracks.first(), folder.tracks) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = playAllLabel)
                }
            }
        }
        itemsIndexed(folder.tracks, key = { _, track -> track.id }) { _, track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                accent = accent,
                onClick = { onPlay(track, folder.tracks) },
                onEdit = { onEditTrack(track) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
