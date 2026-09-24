package com.clean.livetv.data

import android.util.Log
import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader

object M3uParser {
    fun parse(inputStream: InputStream): List<ChannelGroup> {
        val channels = mutableListOf<Channel>()
        var currentName = ""
        var currentLogoUrl = ""
        var currentGroupTitle = ""

        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String? = reader.readLine()

        while (line != null) {
            line = line.trim()
            if (line.startsWith("#EXTINF:")) {
                // Parse attributes
                currentLogoUrl = extractAttribute(line, "tvg-logo")
                currentGroupTitle = extractAttribute(line, "group-title")
                if (currentGroupTitle.isEmpty()) {
                    currentGroupTitle = "Uncategorized"
                }

                // Parse channel name
                val commaIndex = line.lastIndexOf(',')
                if (commaIndex != -1 && commaIndex + 1 < line.length) {
                    currentName = line.substring(commaIndex + 1).trim()
                } else {
                    currentName = "Unknown Channel"
                }
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                // This is the URL line
                if (currentName.isNotEmpty()) {
                    val (baseName, resolution) = parseNameAndResolution(currentName)
                    channels.add(
                        Channel(
                            name = baseName,
                            logoUrl = currentLogoUrl,
                            groupTitle = currentGroupTitle,
                            variants = listOf(ChannelVariant(url = line, resolution = resolution))
                        )
                    )
                }
                // Reset
                currentName = ""
                currentLogoUrl = ""
                currentGroupTitle = ""
            }
            line = reader.readLine()
        }

        // Variant Merging
        val groupedByName = channels.groupBy { it.name }
        val mergedChannels = groupedByName.map { (_, channelList) ->
            val first = channelList.first()
            val allVariants = channelList.flatMap { it.variants }.sortedByDescending { parseResInt(it.resolution) }
            Channel(
                name = first.name,
                logoUrl = first.logoUrl.ifEmpty { channelList.find { it.logoUrl.isNotEmpty() }?.logoUrl ?: "" },
                groupTitle = first.groupTitle,
                variants = allVariants
            )
        }

        // Group by groupTitle
        val groups = mergedChannels.groupBy { it.groupTitle }
        return groups.map { (title, list) ->
            ChannelGroup(title = title, channels = list.sortedBy { it.name })
        }.sortedBy { it.title }
    }

    private fun extractAttribute(line: String, attribute: String): String {
        val regex = "$attribute=\"([^\"]*)\"".toRegex()
        val matchResult = regex.find(line)
        return matchResult?.groups?.get(1)?.value ?: ""
    }

    private fun parseNameAndResolution(rawName: String): Pair<String, String> {
        val regex = "\\((1080p|720p|480p|360p|240p|144p)\\)".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(rawName)
        if (match != null) {
            val resolution = match.groupValues[1]
            val baseName = rawName.replace(match.value, "").trim()
            return Pair(baseName, resolution)
        }
        return Pair(rawName, "unknown")
    }

    private fun parseResInt(resString: String): Int {
        return resString.replace("p", "").toIntOrNull() ?: 0
    }
}