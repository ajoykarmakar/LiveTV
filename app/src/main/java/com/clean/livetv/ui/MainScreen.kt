package com.clean.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.clean.livetv.R
import com.clean.livetv.data.Channel
import com.clean.livetv.data.ChannelGroup
import com.clean.livetv.data.EngagementStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(groups: List<ChannelGroup>, engagementStore: EngagementStore) {
    val coroutineScope = rememberCoroutineScope()
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var selectedGroupIndex by remember { mutableStateOf(0) }

    // Stability Rule: we only snapshot the display groups on initial load or cold start.
    val displayGroups = remember(groups) {
        if (groups.isEmpty()) return@remember emptyList()

        val allChannels = groups.flatMap { it.channels }
        val sortedByPopularity = allChannels
            .filter { it.engagementScore > 0f }
            .sortedByDescending { it.engagementScore }
            .take(15)

        val newGroups = mutableListOf<ChannelGroup>()
        if (sortedByPopularity.isNotEmpty()) {
            newGroups.add(ChannelGroup("Most Watched", sortedByPopularity))
        }
        newGroups.add(ChannelGroup("All Channels", allChannels.sortedBy { it.name }))
        newGroups.addAll(groups)
        
        newGroups
    }

    if (selectedChannel != null) {
        PlayerScreen(channel = selectedChannel!!) { watchSeconds ->
            // On back pressed, record the time watched
            coroutineScope.launch {
                engagementStore.recordWatchTime(selectedChannel!!.name, watchSeconds)
            }
            selectedChannel = null
        }
    } else {
        if (displayGroups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading Channels...", color = Color.White)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Tab Bar inspired by the example image
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(displayGroups) { index, group ->
                        CategoryTab(
                            title = group.title,
                            isSelected = selectedGroupIndex == index,
                            onFocus = { selectedGroupIndex = index },
                            onClick = { selectedGroupIndex = index }
                        )
                    }
                }

                // Main Grid
                val currentGroup = displayGroups.getOrNull(selectedGroupIndex)
                if (currentGroup != null) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(currentGroup.channels) { channel ->
                            ChannelCard(channel = channel) {
                                coroutineScope.launch {
                                    engagementStore.recordOpen(channel.name)
                                }
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
fun CategoryTab(
    title: String,
    isSelected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { state ->
            if (state.isFocused) {
                onFocus()
            }
        },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolderLogo(folderName = title)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun FolderLogo(folderName: String) {
    val upper = folderName.uppercase()
    val localRes = when (upper) {
        "STAR" -> R.drawable.logo_star
        "SONY" -> R.drawable.logo_sony
        else -> null // Will trigger fallback
    }

    if (localRes != null) {
        AsyncImage(
            model = localRes,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    } else {
        // Final Fallback: Stylized typographic monogram badge
        val letter = upper.firstOrNull()?.toString() ?: "O"
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF6A11CB), Color(0xFF2575FC))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f), // Width / Height ratio for TV cards
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1E2124) // Dark card background
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}