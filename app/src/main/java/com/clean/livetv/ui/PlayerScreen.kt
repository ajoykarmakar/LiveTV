package com.clean.livetv.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.tv.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import com.clean.livetv.data.Channel
import kotlinx.coroutines.delay
import android.view.View

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(channel: Channel, onBack: (Long) -> Unit) {
    val context = LocalContext.current
    var currentVariantIndex by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var retryCount by remember { mutableStateOf(0) }
    var isControlsVisible by remember { mutableStateOf(true) }
    
    val startTime = remember { System.currentTimeMillis() }

    val exoPlayer = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Origin" to "https://jiotv.com",
                    "Referer" to "https://jiotv.com/"
                )
            )
            
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
            .build().apply {
                playWhenReady = true
            }
    }

    LaunchedEffect(currentVariantIndex, retryCount) {
        if (currentVariantIndex < channel.variants.size) {
            val variant = channel.variants[currentVariantIndex]
            
            // Clean up URLs to handle common IPTV URL formatting issues
            val rawUrl = variant.url.trim().replace("\"", "").replace("'", "")
            val finalUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl

            val mediaItem = MediaItem.Builder()
                .setUri(finalUrl)
                .build()
            
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
                // If the error is a 403 (Forbidden) or 404 (Not Found), the token is expired or blocked.
                // Do not retry these hard errors; immediately failover to the next stream variant.
                val cause = error.cause
                var isHardHttpError = false
                if (cause is HttpDataSource.InvalidResponseCodeException) {
                    val code = cause.responseCode
                    // 403 Forbidden is often a geoblock or expired token.
                    // However, we added a retry fallback below. If we hit 403 on the *first* attempt,
                    // let's try one more time before skipping the stream completely, just in case 
                    // it was a temporary CDN firewall hiccup.
                    if (code == 404 || code == 401) {
                        isHardHttpError = true
                    } else if (code == 403 && retryCount >= 1) {
                        isHardHttpError = true
                    }
                }

                if (isHardHttpError) {
                    retryCount = 0
                    currentVariantIndex++
                } else if (retryCount < 3) {
                    // Retry temporary glitches (like 403s on first try, timeouts) up to 3 times
                    retryCount++
                } else {
                    retryCount = 0
                    currentVariantIndex++
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    // Reset retry if successfully connected
                    retryCount = 0
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            // Ensure orientation goes back to normal when player is destroyed
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    
    // Timeout fallback (8 seconds total allowed for connection attempt)
    LaunchedEffect(currentVariantIndex, retryCount, isBuffering) {
        if (isBuffering && currentVariantIndex < channel.variants.size) {
            delay(8000)
            if (retryCount < 3) {
                retryCount++
            } else {
                retryCount = 0
                currentVariantIndex++
            }
        }
    }

    BackHandler {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val watchSeconds = (System.currentTimeMillis() - startTime) / 1000L
        onBack(watchSeconds)
    }

    // Auto-hide back button logic
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            delay(3000)
            isControlsVisible = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    // Any touch shows controls
                    isControlsVisible = true
                }
            }
        }
    ) {
        if (errorMessage == null && currentVariantIndex < channel.variants.size) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        
                        setFullscreenButtonClickListener { isFullScreen ->
                            val activity = ctx as? Activity ?: return@setFullscreenButtonClickListener
                            if (isFullScreen) {
                                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                        
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                            isControlsVisible = visibility == View.VISIBLE
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // On-Screen Back Button (Top Left)
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .systemBarsPadding() // Handled Safe Area
                    .padding(16.dp)
            ) {
                Surface(
                    onClick = {
                        val activity = context as? Activity
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        val watchSeconds = (System.currentTimeMillis() - startTime) / 1000L
                        onBack(watchSeconds)
                    },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
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