package com.clean.livetv.data

import kotlinx.serialization.Serializable

@Serializable
data class ChannelVariant(
    val url: String,
    val resolution: String // e.g., "1080p", "720p", or "unknown"
)

@Serializable
data class Channel(
    val name: String,
    val logoUrl: String,
    val groupTitle: String,
    val variants: List<ChannelVariant> // High resolution to low resolution
)

@Serializable
data class ChannelGroup(
    val title: String,
    val channels: List<Channel>
)
