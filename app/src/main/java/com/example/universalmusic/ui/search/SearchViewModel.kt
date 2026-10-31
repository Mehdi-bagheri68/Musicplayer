package com.example.universalmusic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.universalmusic.domain.model.MusicTrack
import com.example.universalmusic.domain.search.AggregatedSearchState
import com.example.universalmusic.domain.search.SearchCategory
import com.example.universalmusic.domain.usecase.SearchMusicUseCase
import com.example.universalmusic.player.PlayerController
import com.example.universalmusic.player.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    searchMusic: SearchMusicUseCase,
    private val playerController: PlayerController
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Tracks only for now: avoids 2 extra requests per search against the Audius rate limit.
    val state: StateFlow<AggregatedSearchState> = _query
        .debounce(350)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { searchMusic(it, SearchCategory.TRACKS) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AggregatedSearchState())

    val playerState: StateFlow<PlayerUiState> = playerController.state

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onTrackClicked(track: MusicTrack) {
        viewModelScope.launch { playerController.play(track) }
    }

    fun togglePlayPause() = playerController.togglePlayPause()
}
