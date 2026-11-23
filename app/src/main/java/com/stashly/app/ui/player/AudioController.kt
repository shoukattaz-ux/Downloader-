package com.stashly.app.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AudioState(val uri: String? = null, val isPlaying: Boolean = false)

/** In-app audio preview player used by the Audio list (play/pause per row). Main-thread only. */
@Singleton
class AudioController @Inject constructor(@ApplicationContext private val context: Context) {
    private var player: ExoPlayer? = null
    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state.asStateFlow()

    private fun ensure(): ExoPlayer = player ?: ExoPlayer.Builder(context).build().also { p ->
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _state.value = _state.value.copy(isPlaying = isPlaying) }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) _state.value = _state.value.copy(isPlaying = false)
            }
        })
        player = p
    }

    fun toggle(uri: String) {
        val p = ensure()
        if (_state.value.uri == uri) {
            if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_ENDED) p.seekTo(0); p.play() }
        } else {
            p.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            p.prepare(); p.play()
            _state.value = AudioState(uri, true)
        }
    }

    fun pause() { player?.pause() }

    fun stopIf(uri: String) { if (_state.value.uri == uri) { player?.stop(); _state.value = AudioState() } }
}
