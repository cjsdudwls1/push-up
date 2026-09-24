package com.pushuprpg.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pushuprpg.app.audio.MusicPlayer
import com.pushuprpg.app.domain.MusicTrack

/**
 * Plays [track] while the app is on screen.
 *
 * Tied to composition, at the root, rather than to a view model: the app going to the background
 * pauses it and coming back resumes it, and changing the setting swaps it on the spot. A player
 * owned by anything that outlives the activity keeps playing over the phone's home screen, which
 * is the bug every app with music ships once.
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
