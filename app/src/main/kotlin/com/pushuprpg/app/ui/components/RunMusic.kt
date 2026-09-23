package com.pushuprpg.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pushuprpg.app.audio.MusicPlayer
import com.pushuprpg.app.domain.MusicTrack

/**
 * Plays [track] for as long as the calling screen is on screen, and not a moment longer.
 *
 * Tied to composition rather than to a view model because the music belongs to the screen being
 * looked at: leaving a run stops it, the app going to the background pauses it, and changing the
 * setting mid-run swaps it. A player owned by anything longer-lived keeps playing over the home
 * screen, which is the bug every app with music ships once.
 */
@Composable
fun RunMusic(track: MusicTrack, player: MusicPlayer) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(track, lifecycleOwner) {
        player.play(track)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.stop()
        }
    }
}
