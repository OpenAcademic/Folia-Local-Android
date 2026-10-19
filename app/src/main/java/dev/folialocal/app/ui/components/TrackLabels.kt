package dev.folialocal.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import dev.folialocal.app.R
import dev.folialocal.app.data.model.AlbumGroup
import dev.folialocal.app.data.model.ArtistGroup
import dev.folialocal.app.data.model.Track

/**
 * 空元数据的展示标签。数据层保留原始空串，展示层按当前语言解析，
 * 保证专辑/艺术家分组在任何语言下都能正确聚合。
 */
@Composable
@ReadOnlyComposable
fun Track.artistLabel(): String = artist.ifBlank { stringResource(R.string.unknown_artist) }

@Composable
@ReadOnlyComposable
fun Track.albumLabel(): String = album.ifBlank { stringResource(R.string.unknown_album) }

@Composable
@ReadOnlyComposable
fun Track.folderLabel(): String = folder.ifBlank { stringResource(R.string.root_folder) }

@Composable
@ReadOnlyComposable
fun AlbumGroup.nameLabel(): String = name.ifBlank { stringResource(R.string.unknown_album) }

@Composable
@ReadOnlyComposable
fun AlbumGroup.artistLabel(): String = artist.ifBlank { stringResource(R.string.unknown_artist) }

@Composable
@ReadOnlyComposable
fun ArtistGroup.nameLabel(): String = name.ifBlank { stringResource(R.string.unknown_artist) }
