package com.clean.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.compose.runtime.CompositionLocalProvider
import com.clean.livetv.data.PlaylistRepository

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveTvApp() {
    val context = LocalContext.current
    
    // Strict memory cap for Coil
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.15) // Max 15% of available memory for images
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }

    val repository = remember { PlaylistRepository(context) }
    
    LaunchedEffect(Unit) {
        repository.loadAndSync()
    }

    val groups by repository.channelsFlow.collectAsState()

    CompositionLocalProvider(LocalImageLoader provides imageLoader) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                MainScreen(groups = groups)
            }
        }
    }
}