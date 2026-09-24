package com.clean.livetv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.clean.livetv.data.Channel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(channel: Channel, onBack: () -> Unit) {
    val context = LocalContext.current
    var currentVariantIndex by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(currentVariantIndex) {
        if (currentVariantIndex < channel.variants.size) {
            val variant = channel.variants[currentVariantIndex]
            val mediaItem = MediaItem.fromUri(variant.url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            errorMessage = null
            isBuffering = true
        } else {
            errorMessage = "Stream temporarily offline."
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Try next variant on error
                currentVariantIndex++
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    
    // Timeout fallback (4 seconds)
    LaunchedEffect(currentVariantIndex, isBuffering) {
        if (isBuffering && currentVariantIndex < channel.variants.size) {
            delay(4000)
            // If still buffering after 4 seconds, consider it a timeout and move to next variant
            currentVariantIndex++
        }
    }

    BackHandler {
        onBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (errorMessage == null && currentVariantIndex < channel.variants.size) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = errorMessage!!,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}