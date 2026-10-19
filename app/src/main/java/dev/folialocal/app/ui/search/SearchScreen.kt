package dev.folialocal.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.folialocal.app.R
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.ui.components.EmptyState
import dev.folialocal.app.ui.components.TrackRow

@Composable
fun SearchScreen(
    query: String,
    results: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    accent: Color,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onEditTrack: (Track) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val backLabel = stringResource(R.string.back)
    val searchHint = stringResource(R.string.search_hint)
    val clearLabel = stringResource(R.string.search_clear)
    val emptyTitle = stringResource(R.string.search_title)
    val emptyBody = stringResource(R.string.search_body)
    val noResultsTitle = stringResource(R.string.search_empty_title, query)
    val noResultsBody = stringResource(R.string.search_empty_body)
    val resultsCount = stringResource(R.string.search_results_count, results.size)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel)
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(searchHint) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = clearLabel)
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
        }

        when {
            query.isBlank() -> EmptyState(
                icon = Icons.Rounded.Search,
                title = emptyTitle,
                body = emptyBody,
                modifier = Modifier.weight(1f),
            )
            results.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Search,
                title = noResultsTitle,
                body = noResultsBody,
                modifier = Modifier.weight(1f),
            )
            else -> {
                Text(
                    text = resultsCount,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(results, key = { _, track -> track.id }) { _, track ->
                        TrackRow(
                            track = track,
                            isCurrent = track.id == currentTrackId,
                            isPlaying = isPlaying,
                            accent = accent,
                            onClick = { onPlay(track, results) },
                            onEdit = { onEditTrack(track) },
                        )
                    }
                }
            }
        }
    }
}
