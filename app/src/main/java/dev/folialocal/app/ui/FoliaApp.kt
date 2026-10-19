package dev.folialocal.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.folialocal.app.R
import dev.folialocal.app.data.audio.AudioEffects
import dev.folialocal.app.data.library.ScanProgress
import dev.folialocal.app.data.model.LibrarySnapshot
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.data.playback.PlaybackUiState
import dev.folialocal.app.data.settings.AppSettings
import dev.folialocal.app.ui.adaptive.FoldInfo
import dev.folialocal.app.ui.adaptive.WindowWidthClass
import dev.folialocal.app.ui.adaptive.rememberFoldInfo
import dev.folialocal.app.ui.adaptive.rememberWindowWidthClass
import dev.folialocal.app.ui.components.MiniPlayer
import dev.folialocal.app.ui.library.AlbumDetailScreen
import dev.folialocal.app.ui.library.AlbumsScreen
import dev.folialocal.app.ui.library.ArtistDetailScreen
import dev.folialocal.app.ui.library.ArtistsScreen
import dev.folialocal.app.ui.library.FolderDetailScreen
import dev.folialocal.app.ui.library.FoldersScreen
import dev.folialocal.app.ui.library.SongsScreen
import dev.folialocal.app.ui.navigation.Destination
import dev.folialocal.app.ui.onboarding.OnboardingScreen
import dev.folialocal.app.ui.player.PlayerScreen
import dev.folialocal.app.ui.player.TrackEditDialog
import dev.folialocal.app.ui.search.SearchScreen
import dev.folialocal.app.ui.settings.SettingsScreen
import dev.folialocal.app.ui.theme.FoliaBackground
import dev.folialocal.app.ui.theme.FoliaSurface
import dev.folialocal.app.ui.theme.FoliaText
import dev.folialocal.app.ui.theme.FoliaTheme
import dev.folialocal.app.vm.LibraryViewModel
import dev.folialocal.app.vm.PlaybackViewModel
import dev.folialocal.app.vm.SettingsViewModel

private data class NavItem(
    val destination: Destination,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem(Destination.Songs, R.string.nav_songs, Icons.Rounded.LibraryMusic),
    NavItem(Destination.Albums, R.string.nav_albums, Icons.Rounded.Album),
    NavItem(Destination.Artists, R.string.nav_artists, Icons.Rounded.Person),
    NavItem(Destination.Folders, R.string.nav_folders, Icons.Rounded.Folder),
    NavItem(Destination.Settings, R.string.nav_settings, Icons.Rounded.Settings),
)

@Composable
fun FoliaRoot(activity: Activity) {
    val libraryViewModel: LibraryViewModel = viewModel()
    val playbackViewModel: PlaybackViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val index by libraryViewModel.index.collectAsStateWithLifecycle()
    val snapshot by libraryViewModel.snapshot.collectAsStateWithLifecycle()
    val rootName by libraryViewModel.rootName.collectAsStateWithLifecycle()
    val progress by libraryViewModel.progress.collectAsStateWithLifecycle()
    val destination by libraryViewModel.destination.collectAsStateWithLifecycle()
    val canGoBack by libraryViewModel.canGoBack.collectAsStateWithLifecycle()
    val query by libraryViewModel.query.collectAsStateWithLifecycle()
    val searchResults by libraryViewModel.searchResults.collectAsStateWithLifecycle()

    val playbackState by playbackViewModel.playbackState.collectAsStateWithLifecycle()
    val currentTrack by playbackViewModel.currentTrack.collectAsStateWithLifecycle()
    val lyrics by playbackViewModel.lyrics.collectAsStateWithLifecycle()
    val queue by playbackViewModel.queueTracks.collectAsStateWithLifecycle()
    val accentInt by playbackViewModel.accentColor.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val audioEffects by settingsViewModel.audioEffects.collectAsStateWithLifecycle()

    val widthClass = rememberWindowWidthClass()
    val foldInfo = rememberFoldInfo(activity)
    val accent = Color(accentInt)
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var editingTrack by remember { mutableStateOf<Track?>(null) }

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && canGoBack) { libraryViewModel.goBack() }

    FoliaTheme(accent = accent) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = FoliaBackground,
            contentColor = FoliaText,
        ) {
            Box(Modifier.fillMaxSize()) {
            if (index.rootUri.isBlank()) {
                OnboardingScreen(
                    progress = progress,
                    onPickFolder = libraryViewModel::setRoot,
                )
                return@Box
            }

            val content: @Composable () -> Unit = {
                DestinationContent(
                    destination = destination,
                    snapshot = snapshot,
                    rootName = rootName,
                    progress = progress,
                    playbackState = playbackState,
                    query = query,
                    searchResults = searchResults,
                    accent = accent,
                    settings = settings,
                    libraryViewModel = libraryViewModel,
                    playbackViewModel = playbackViewModel,
                    settingsViewModel = settingsViewModel,
                    onEditTrack = { editingTrack = it },
                )
                val editing = editingTrack
                if (editing != null) {
                    TrackEditDialog(
                        track = editing,
                        onDismiss = { editingTrack = null },
                        onSaved = playbackViewModel::reloadLyrics,
                    )
                }
            }

            if (showPlayer && currentTrack != null) {
                PlayerScreen(
                    track = currentTrack,
                    playbackState = playbackState,
                    lyrics = lyrics,
                    queue = queue,
                    accentInt = accentInt,
                    settings = settings,
                    audioEffects = audioEffects,
                    isFullScreen = true,
                    tabletopGap = if (foldInfo.isSeparating && !foldInfo.isVertical) {
                        foldInfo.hingeHeightDp.dp
                    } else {
                        0.dp
                    },
                    onClose = { showPlayer = false },
                    onTogglePlayPause = playbackViewModel::togglePlayPause,
                    onNext = playbackViewModel::next,
                    onPrevious = playbackViewModel::previous,
                    onSeek = playbackViewModel::seekTo,
                    onToggleShuffle = playbackViewModel::setShuffle,
                    onCycleRepeat = playbackViewModel::cycleRepeatMode,
                    onPlayAt = playbackViewModel::playAt,
                    onAudioEffectsChange = settingsViewModel::setAudioEffects,
                    onStyleChange = settingsViewModel::setVisualStyle,
                    onTrackMetadataSaved = playbackViewModel::reloadLyrics,
                    onLyricsImported = {
                        playbackViewModel.reloadLyrics()
                        libraryViewModel.rescan()
                    },
                )
                return@Box
            }

            when (widthClass) {
                WindowWidthClass.Compact -> CompactLayout(
                    destination = destination,
                    currentTrack = currentTrack,
                    playbackState = playbackState,
                    accent = accent,
                    onOpenPlayer = { showPlayer = true },
                    onTogglePlayPause = playbackViewModel::togglePlayPause,
                    onNext = playbackViewModel::next,
                    onNavigate = libraryViewModel::navigateRoot,
                    content = content,
                )
                WindowWidthClass.Medium -> MediumLayout(
                    destination = destination,
                    currentTrack = currentTrack,
                    playbackState = playbackState,
                    accent = accent,
                    onOpenPlayer = { showPlayer = true },
                    onTogglePlayPause = playbackViewModel::togglePlayPause,
                    onNext = playbackViewModel::next,
                    onNavigate = libraryViewModel::navigateRoot,
                    content = content,
                )
                WindowWidthClass.Expanded -> ExpandedLayout(
                    destination = destination,
                    currentTrack = currentTrack,
                    playbackState = playbackState,
                    lyrics = lyrics,
                    queue = queue,
                    accentInt = accentInt,
                    accent = accent,
                    settings = settings,
                    audioEffects = audioEffects,
                    foldInfo = foldInfo,
                    onNavigate = libraryViewModel::navigateRoot,
                    playbackViewModel = playbackViewModel,
                    libraryViewModel = libraryViewModel,
                    onAudioEffectsChange = settingsViewModel::setAudioEffects,
                    onStyleChange = settingsViewModel::setVisualStyle,
                    content = content,
                )
            }
        }
        }
    }
}

@Composable
private fun CompactLayout(
    destination: Destination,
    currentTrack: Track?,
    playbackState: PlaybackUiState,
    accent: Color,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onNavigate: (Destination) -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = FoliaBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Column {
                if (currentTrack != null) {
                    MiniPlayer(
                        track = currentTrack,
                        isPlaying = playbackState.isPlaying,
                        positionMs = playbackState.positionMs,
                        durationMs = playbackState.durationMs,
                        accent = accent,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onOpen = onOpenPlayer,
                    )
                }
                NavigationBar(containerColor = FoliaSurface) {
                    val selected = railSelected(destination)
                    navItems.forEach { item ->
                        val label = stringResource(item.labelRes)
                        NavigationBarItem(
                            selected = selected == item.destination,
                            onClick = { onNavigate(item.destination) },
                            icon = { Icon(item.icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            content()
        }
    }
}

@Composable
private fun MediumLayout(
    destination: Destination,
    currentTrack: Track?,
    playbackState: PlaybackUiState,
    accent: Color,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onNavigate: (Destination) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        FoliaRail(destination = destination, onNavigate = onNavigate)
        Column(Modifier.weight(1f)) {
            Box(Modifier.weight(1f)) { content() }
            if (currentTrack != null) {
                MiniPlayer(
                    track = currentTrack,
                    isPlaying = playbackState.isPlaying,
                    positionMs = playbackState.positionMs,
                    durationMs = playbackState.durationMs,
                    accent = accent,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onOpen = onOpenPlayer,
                )
            }
        }
    }
}

@Composable
private fun ExpandedLayout(
    destination: Destination,
    currentTrack: Track?,
    playbackState: PlaybackUiState,
    lyrics: dev.folialocal.app.data.lyrics.Lyrics?,
    queue: List<Track>,
    accentInt: Int,
    accent: Color,
    settings: AppSettings,
    audioEffects: AudioEffects,
    foldInfo: FoldInfo,
    onNavigate: (Destination) -> Unit,
    playbackViewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onAudioEffectsChange: (AudioEffects) -> Unit,
    onStyleChange: (dev.folialocal.app.data.settings.VisualStyle) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        FoliaRail(destination = destination, onNavigate = onNavigate)
        val showPlayerPane = currentTrack != null && destination != Destination.Settings
        if (!showPlayerPane) {
            Box(Modifier.weight(1f)) { content() }
            return@Row
        }

        BoxWithConstraints(Modifier.weight(1f)) {
            val verticalHinge = if (foldInfo.isSeparating && foldInfo.isVertical) {
                foldInfo.hingeWidthDp.dp
            } else {
                0.dp
            }
            if (verticalHinge > 0.dp) {
                val paneWidth = (maxWidth - verticalHinge) / 2
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(paneWidth)) { content() }
                    Spacer(Modifier.width(verticalHinge))
                    Box(Modifier.width(paneWidth)) {
                        PlayerPane(
                            track = currentTrack,
                            playbackState = playbackState,
                            lyrics = lyrics,
                            queue = queue,
                            accentInt = accentInt,
                            accent = accent,
                            settings = settings,
                            audioEffects = audioEffects,
                            foldInfo = foldInfo,
                            playbackViewModel = playbackViewModel,
                            onAudioEffectsChange = onAudioEffectsChange,
                            onStyleChange = onStyleChange,
                            onTrackMetadataSaved = playbackViewModel::reloadLyrics,
                            onLyricsImported = {
                                playbackViewModel.reloadLyrics()
                                libraryViewModel.rescan()
                            },
                        )
                    }
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.56f)) { content() }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(0.44f)) {
                        PlayerPane(
                            track = currentTrack,
                            playbackState = playbackState,
                            lyrics = lyrics,
                            queue = queue,
                            accentInt = accentInt,
                            accent = accent,
                            settings = settings,
                            audioEffects = audioEffects,
                            foldInfo = foldInfo,
                            playbackViewModel = playbackViewModel,
                            onAudioEffectsChange = onAudioEffectsChange,
                            onStyleChange = onStyleChange,
                            onTrackMetadataSaved = playbackViewModel::reloadLyrics,
                            onLyricsImported = {
                                playbackViewModel.reloadLyrics()
                                libraryViewModel.rescan()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPane(
    track: Track?,
    playbackState: PlaybackUiState,
    lyrics: dev.folialocal.app.data.lyrics.Lyrics?,
    queue: List<Track>,
    accentInt: Int,
    accent: Color,
    settings: AppSettings,
    audioEffects: AudioEffects,
    foldInfo: FoldInfo,
    playbackViewModel: PlaybackViewModel,
    onAudioEffectsChange: (AudioEffects) -> Unit,
    onStyleChange: (dev.folialocal.app.data.settings.VisualStyle) -> Unit,
    onTrackMetadataSaved: () -> Unit,
    onLyricsImported: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0E)),
    ) {
        PlayerScreen(
            track = track,
            playbackState = playbackState,
            lyrics = lyrics,
            queue = queue,
            accentInt = accentInt,
            settings = settings,
            audioEffects = audioEffects,
            isFullScreen = false,
            tabletopGap = if (foldInfo.isSeparating && !foldInfo.isVertical) {
                foldInfo.hingeHeightDp.dp
            } else {
                0.dp
            },
            onClose = null,
            onTogglePlayPause = playbackViewModel::togglePlayPause,
            onNext = playbackViewModel::next,
            onPrevious = playbackViewModel::previous,
            onSeek = playbackViewModel::seekTo,
            onToggleShuffle = playbackViewModel::setShuffle,
            onCycleRepeat = playbackViewModel::cycleRepeatMode,
            onPlayAt = playbackViewModel::playAt,
            onAudioEffectsChange = onAudioEffectsChange,
            onStyleChange = onStyleChange,
            onTrackMetadataSaved = onTrackMetadataSaved,
            onLyricsImported = onLyricsImported,
        )
    }
}

@Composable
private fun FoliaRail(
    destination: Destination,
    onNavigate: (Destination) -> Unit,
) {
    NavigationRail(
        containerColor = FoliaSurface,
        windowInsets = WindowInsets(0, 0, 0, 0),
        header = {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "Folia",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        },
    ) {
        Spacer(Modifier.height(8.dp))
        val selected = railSelected(destination)
        navItems.forEach { item ->
            val label = stringResource(item.labelRes)
            NavigationRailItem(
                selected = selected == item.destination,
                onClick = { onNavigate(item.destination) },
                icon = { Icon(item.icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}

private fun railSelected(destination: Destination): Destination = when (destination) {
    is Destination.AlbumDetail -> Destination.Albums
    is Destination.ArtistDetail -> Destination.Artists
    is Destination.FolderDetail -> Destination.Folders
    Destination.Search -> Destination.Songs
    else -> destination
}

@Composable
private fun DestinationContent(
    destination: Destination,
    snapshot: LibrarySnapshot,
    rootName: String,
    progress: ScanProgress,
    playbackState: PlaybackUiState,
    query: String,
    searchResults: List<Track>,
    accent: Color,
    settings: AppSettings,
    libraryViewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
    onEditTrack: (Track) -> Unit,
) {
    val onPlay: (Track, List<Track>) -> Unit = { track, queue ->
        playbackViewModel.playTrack(track, queue)
    }
    when (destination) {
        Destination.Songs -> SongsScreen(
            snapshot = snapshot,
            rootName = rootName,
            progress = progress,
            currentTrackId = playbackState.currentTrackId,
            isPlaying = playbackState.isPlaying,
            accent = accent,
            onPlay = onPlay,
            onSearch = { libraryViewModel.push(Destination.Search) },
            onRescan = libraryViewModel::rescan,
            onEditTrack = onEditTrack,
        )
        Destination.Albums -> AlbumsScreen(
            albums = snapshot.albums,
            onOpenAlbum = { album -> libraryViewModel.push(Destination.AlbumDetail(album.key)) },
        )
        Destination.Artists -> ArtistsScreen(
            artists = snapshot.artists,
            onOpenArtist = { artist -> libraryViewModel.push(Destination.ArtistDetail(artist.name)) },
        )
        Destination.Folders -> FoldersScreen(
            folders = snapshot.folders,
            onOpenFolder = { folder -> libraryViewModel.push(Destination.FolderDetail(folder.path)) },
        )
        Destination.Search -> SearchScreen(
            query = query,
            results = searchResults,
            currentTrackId = playbackState.currentTrackId,
            isPlaying = playbackState.isPlaying,
            accent = accent,
            onQueryChange = libraryViewModel::setQuery,
            onBack = libraryViewModel::goBack,
            onPlay = onPlay,
            onEditTrack = onEditTrack,
        )
        Destination.Settings -> SettingsScreen(
            settings = settings,
            rootName = rootName,
            trackCount = snapshot.tracks.size,
            onStyleChange = settingsViewModel::setVisualStyle,
            onFontScaleChange = settingsViewModel::setLyricFontScale,
            onCoverAccentChange = settingsViewModel::setCoverAccent,
            onRescan = libraryViewModel::rescan,
            onPickFolder = libraryViewModel::setRoot,
            onClearLibrary = libraryViewModel::clearLibrary,
        )
        is Destination.AlbumDetail -> AlbumDetailScreen(
            album = snapshot.albums.firstOrNull { it.key == destination.key },
            currentTrackId = playbackState.currentTrackId,
            isPlaying = playbackState.isPlaying,
            accent = accent,
            onBack = libraryViewModel::goBack,
            onPlay = onPlay,
            onEditTrack = onEditTrack,
        )
        is Destination.ArtistDetail -> ArtistDetailScreen(
            artist = snapshot.artists.firstOrNull { it.name == destination.name },
            currentTrackId = playbackState.currentTrackId,
            isPlaying = playbackState.isPlaying,
            accent = accent,
            onBack = libraryViewModel::goBack,
            onPlay = onPlay,
            onEditTrack = onEditTrack,
        )
        is Destination.FolderDetail -> FolderDetailScreen(
            folder = snapshot.folders.firstOrNull { it.path == destination.path },
            currentTrackId = playbackState.currentTrackId,
            isPlaying = playbackState.isPlaying,
            accent = accent,
            onBack = libraryViewModel::goBack,
            onPlay = onPlay,
            onEditTrack = onEditTrack,
        )
    }
}
