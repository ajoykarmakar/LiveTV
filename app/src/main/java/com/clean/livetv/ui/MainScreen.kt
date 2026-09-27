package com.clean.livetv.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.core.content.edit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.clean.livetv.data.Channel
import com.clean.livetv.data.ChannelGroup
import com.clean.livetv.data.EngagementStore
import com.clean.livetv.data.StreamHealthChecker
import com.clean.livetv.data.StreamStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF263238)) // A modern slate/blue-grey background
            .systemBarsPadding(), // Ensures content does not overlap with status bar / navigation bar
        content = content
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(groups: List<ChannelGroup>, engagementStore: EngagementStore) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean("is_first_launch", true)) }

    val coroutineScope = rememberCoroutineScope()
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var selectedGroup by remember { mutableStateOf<ChannelGroup?>(null) }

    LaunchedEffect(groups) {
        if (groups.isNotEmpty() && isFirstLaunch) {
            prefs.edit { putBoolean("is_first_launch", false) }
            isFirstLaunch = false
        }
    }

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

        newGroups.addAll(groups)
        
        newGroups
    }

    // Responsive calculations
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isMobile = screenWidth < 600
    val gridColumns = if (isMobile) 1 else if (screenWidth < 900) 2 else 4
    val screenPadding = if (isMobile) 16.dp else 32.dp
    val spacing = if (isMobile) 12.dp else 16.dp

    // Dynamic background for eye-catchy UI inspired by the image
    AppBackground {
        val currentChannel = selectedChannel
        if (currentChannel != null) {
            PlayerScreen(channel = currentChannel) { watchSeconds ->
                coroutineScope.launch {
                    engagementStore.recordWatchTime(currentChannel.name, watchSeconds)
                }
                selectedChannel = null
            }
        } else if (selectedGroup != null) {
            GroupScreen(
                group = selectedGroup!!,
                onBack = { selectedGroup = null },
                onChannelClick = { channel ->
                    coroutineScope.launch {
                        engagementStore.recordOpen(channel.name)
                    }
                    selectedChannel = channel
                }
            )
        } else {
            if (displayGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                        if (isFirstLaunch) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "We are setting up the app for you, please wait a little...",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(start = screenPadding, top = 16.dp, end = screenPadding, bottom = screenPadding),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayGroups) { group ->
                            DashboardCard(
                                title = group.title,
                                subtitle = "${group.channels.size} Channels",
                                logoUrl = group.channels.firstOrNull()?.logoUrl ?: "",
                                folderNameForSystemIcon = group.title,
                                isMobile = isMobile
                            ) {
                                selectedGroup = group
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupScreen(
    group: ChannelGroup,
    onBack: () -> Unit,
    onChannelClick: (Channel) -> Unit
) {
    BackHandler(onBack = onBack)
    
    // Responsive calculations
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isMobile = screenWidth < 600
    val gridColumns = if (isMobile) 1 else if (screenWidth < 900) 2 else 4
    val screenPadding = if (isMobile) 16.dp else 32.dp
    val spacing = if (isMobile) 12.dp else 16.dp

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(start = screenPadding, top = if (isMobile) 16.dp else 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(end = 16.dp),
                    colors = IconButtonDefaults.colors(
                        // High contrast against dark background
                        contentColor = Color.White,
                        focusedContentColor = Color.Black,
                        focusedContainerColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                MediaLogo(
                    name = group.title,
                    logoUrl = group.channels.firstOrNull()?.logoUrl ?: "",
                    modifier = Modifier.size(if (isMobile) 32.dp else 40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = group.title,
                        style = if (isMobile) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(start = screenPadding, top = 16.dp, end = screenPadding, bottom = screenPadding),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.fillMaxSize()
            ) {
                items(group.channels) { channel ->
                    DashboardCard(
                        title = channel.name,
                        subtitle = null,
                        logoUrl = channel.logoUrl,
                        channel = channel,
                        isMobile = isMobile
                    ) {
                        onChannelClick(channel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DashboardCard(
    title: String,
    subtitle: String?,
    logoUrl: String,
    channel: Channel? = null,
    folderNameForSystemIcon: String? = null,
    isMobile: Boolean = false,
    onClick: () -> Unit
) {
    var streamStatus by remember { mutableStateOf(StreamStatus.CHECKING) }

    // High Performance Detached Checking
    LaunchedEffect(channel) {
        if (channel != null) {
            delay(800) // Don't block UI if user is fast scrolling!
            streamStatus = StreamHealthChecker.checkChannel(channel)
        }
    }

    Card(
        onClick = { if (!isMobile) onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isMobile) 80.dp else 110.dp) // Dynamic height
            .then(if (isMobile) Modifier.clickable { onClick() } else Modifier),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = Color.White.copy(alpha = 0.3f), elevation = 8.dp)),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1E242B),
            focusedContainerColor = Color(0xFF2C353F)
        ),
        border = CardDefaults.border(
            border = Border(border = BorderStroke(1.dp, Color(0xFF37474F))),
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = if (isMobile) 16.dp else 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Box {
                    MediaLogo(
                        name = folderNameForSystemIcon ?: title,
                        logoUrl = logoUrl,
                        modifier = Modifier.size(if (isMobile) 40.dp else 42.dp)
                    )
                    
                    // Status Indicator Badge (Only for Channels)
                    if (channel != null) {
                        val statusColor = when (streamStatus) {
                            StreamStatus.ONLINE -> Color(0xFF4CAF50) // Green
                            StreamStatus.OFFLINE -> Color(0xFFF44336) // Red
                            StreamStatus.CHECKING -> Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .clip(CircleShape)
                                .background(statusColor)
                                .border(1.5.dp, Color(0xFF1E242B), CircleShape)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (subtitle != null) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (channel != null) {
                        // Show text status for channels
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (streamStatus) {
                                StreamStatus.ONLINE -> "Online"
                                StreamStatus.OFFLINE -> "Offline"
                                StreamStatus.CHECKING -> "Checking..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (streamStatus) {
                                StreamStatus.ONLINE -> Color(0xFF4CAF50)
                                StreamStatus.OFFLINE -> Color(0xFFF44336)
                                StreamStatus.CHECKING -> Color.Gray
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Top Right HD Badge
            if (channel != null && channel.hasHdOrHigher && streamStatus == StreamStatus.ONLINE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF293D52))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "HD",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MediaLogo(name: String, logoUrl: String, modifier: Modifier = Modifier) {
    val systemIcon = when (name) {
        "All Channels" -> Icons.Default.PlayArrow
        "Most Watched" -> Icons.Default.Star
        "Cartoon" -> Icons.Default.Face
        "HD Channels" -> Icons.Default.Star
        "Other Channels", "Other" -> Icons.AutoMirrored.Filled.List
        else -> null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.2f)), // Solid white for network logos
        contentAlignment = Alignment.Center
    ) {
        if (systemIcon != null) {
            Icon(
                imageVector = systemIcon,
                contentDescription = null,
                tint = Color(0xFF1E242B),
                modifier = Modifier.size(38.dp)
            )
        } else if (logoUrl.isNotEmpty()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(logoUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                    .addHeader("Referer", "https://jiotv.com/")
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(2.dp),
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "...", color = Color.Gray, fontSize = 12.sp)
                    }
                },
                error = {
                    val letter = if (name.isNotEmpty()) name.uppercase().first().toString() else "O"
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = letter,
                            color = Color.Black,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        } else {
            // Final Fallback: Stylized monogram
            val letter = if (name.isNotEmpty()) name.uppercase().first().toString() else "O"
            Text(
                text = letter,
                color = Color.Black,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}