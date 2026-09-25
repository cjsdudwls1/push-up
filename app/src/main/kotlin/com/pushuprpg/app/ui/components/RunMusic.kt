package com.pushuprpg.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    // Only the track the app opens with gives way to music already playing: one changed to while
    // the app is open was picked in settings, and picking a track is hearing it.
    var opening by remember { mutableStateOf(true) }
    DisposableEffect(track, lifecycleOwner) {
        player.play(track, yieldToOtherMusic = opening)
        opening = false
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
