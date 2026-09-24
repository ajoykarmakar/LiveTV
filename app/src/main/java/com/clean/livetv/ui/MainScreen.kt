package com.clean.livetv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.clean.livetv.data.Channel
import com.clean.livetv.data.ChannelGroup

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(groups: List<ChannelGroup>) {
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }

    if (selectedChannel != null) {
        PlayerScreen(channel = selectedChannel!!) {
            // On back pressed
            selectedChannel = null
        }
    } else {
        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading Channels...")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
            ) {
                items(groups) { group ->
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp, top = 16.dp)
                    )
                    
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(group.channels) { channel ->
                            ChannelCard(channel = channel) {
                                selectedChannel = channel
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(160.dp, 100.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}