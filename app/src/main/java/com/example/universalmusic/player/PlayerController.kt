package com.example.universalmusic.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.universalmusic.domain.model.MusicTrack
import com.example.universalmusic.domain.provider.PlaybackResolution
import com.example.universalmusic.domain.provider.ProviderRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerUiState(
    val trackId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/** Minimal in-app player. Must be used from the main thread (viewModelScope). */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerRegistry: ProviderRegistry
) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(isLoading = false, isPlaying = false, errorMessage = "Playback failed") }
        }
    }

    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(listener) }
    }

    suspend fun play(track: MusicTrack) {
        _state.value = PlayerUiState(
            trackId = track.id, title = track.title, artist = track.artistName, isLoading = true
        )

        val resolution = try {
            providerRegistry.getProvider(track.providerId)?.resolvePlayback(track)
                ?: PlaybackResolution.Unavailable("Provider not available")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PlaybackResolution.Unavailable("Could not resolve stream")
        }

        val uri = when (resolution) {
            is PlaybackResolution.Ready -> resolution.descriptor.uri
            is PlaybackResolution.PreviewOnly -> resolution.descriptor.uri
            else -> null
        }

        if (uri == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "This track cannot be played in-app") }
            return
        }

        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(track.title).setArtist(track.artistName).build()
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (_state.value.trackId == null) return
        if (player.isPlaying) player.pause() else player.play()
    }
}
