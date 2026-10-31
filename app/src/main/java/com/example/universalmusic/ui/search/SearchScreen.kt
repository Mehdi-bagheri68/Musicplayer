package com.example.universalmusic.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.universalmusic.R
import com.example.universalmusic.domain.model.PlaybackAvailability
import com.example.universalmusic.domain.model.ProviderId
import com.example.universalmusic.domain.model.TrackSourceGroup
import com.example.universalmusic.domain.search.ProviderSearchState
import com.example.universalmusic.domain.usecase.SearchMusicUseCase
import com.example.universalmusic.player.PlayerUiState

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (player.trackId != null) MiniPlayer(player, onToggle = viewModel::togglePlayPause)
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear_description))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            ProviderStatusRow(state.providerStates)

            val trimmed = query.trim()
            val isCurrent = state.query == trimmed
            if (isCurrent && !state.isComplete) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                trimmed.length < SearchMusicUseCase.MIN_QUERY_LENGTH ->
                    CenterMessage(stringResource(R.string.search_empty_prompt))
                isCurrent && state.isComplete && state.trackGroups.isEmpty() ->
                    CenterMessage(stringResource(R.string.search_no_results))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.trackGroups, key = { it.primaryTrack.id }) { group ->
                        TrackRow(group, onClick = { viewModel.onTrackClicked(group.primaryTrack) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProviderStatusRow(states: Map<ProviderId, ProviderSearchState>) {
    if (states.isEmpty()) return
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        states.forEach { (id, s) ->
            val label = providerStateLabel(s)
            AssistChip(onClick = {}, label = { Text("${id.value}: $label") })
        }
    }
}

@Composable
private fun providerStateLabel(state: ProviderSearchState): String = when (state) {
    ProviderSearchState.Idle -> ""
    ProviderSearchState.Loading -> stringResource(R.string.provider_state_loading)
    is ProviderSearchState.Success -> stringResource(R.string.provider_state_results, state.count)
    is ProviderSearchState.RateLimited -> stringResource(R.string.provider_state_rate_limited)
    is ProviderSearchState.AuthenticationRequired -> stringResource(R.string.provider_state_auth_required)
    is ProviderSearchState.Failed -> stringResource(R.string.provider_state_error)
    ProviderSearchState.Disabled -> stringResource(R.string.provider_state_disabled)
}

@Composable
private fun availabilityLabel(a: PlaybackAvailability): String = when (a) {
    PlaybackAvailability.FULL_STREAM -> stringResource(R.string.availability_full_stream)
    PlaybackAvailability.PREVIEW -> stringResource(R.string.availability_preview)
    PlaybackAvailability.OFFICIAL_PLAYER -> stringResource(R.string.availability_official_player)
    PlaybackAvailability.EXTERNAL_ONLY -> stringResource(R.string.availability_external_only)
    PlaybackAvailability.UNAVAILABLE -> stringResource(R.string.availability_unavailable)
}

@Composable
private fun TrackRow(group: TrackSourceGroup, onClick: () -> Unit) {
    val track = group.primaryTrack
    ListItem(
        modifier = Modifier.clickable(enabled = track.isPlayableInApp, onClick = onClick),
        leadingContent = {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
            )
        },
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(track.artistName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.source_attribution_prefix, track.providerId.value) +
                        if (group.alternatives.isNotEmpty()) " • " + stringResource(R.string.more_sources, group.alternatives.size) else "",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        trailingContent = {
            Text(availabilityLabel(track.playbackAvailability), style = MaterialTheme.typography.labelSmall)
        }
    )
}

@Composable
private fun MiniPlayer(state: PlayerUiState, onToggle: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(state.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                val subtitle = state.errorMessage ?: state.artist.orEmpty()
                Text(
                    subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play)
                    )
                }
            }
        }
    }
}
