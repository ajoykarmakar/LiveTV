package com.clean.livetv.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3uParser {
    fun parse(inputStream: InputStream, engagementStore: EngagementStore): List<ChannelGroup> {
        val channels = mutableListOf<Channel>()
        var currentName = ""
        var currentLogoUrl = ""
        
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String? = reader.readLine()

        while (line != null) {
            line = line.trim()
            if (line.startsWith("#EXTINF:")) {
                currentLogoUrl = extractAttribute(line, "tvg-logo")
                val commaIndex = line.lastIndexOf(',')
                if (commaIndex != -1 && commaIndex + 1 < line.length) {
                    currentName = line.substring(commaIndex + 1).trim()
                } else {
                    currentName = "Unknown Channel"
                }
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                if (currentName.isNotEmpty()) {
                    val (baseName, resolution) = parseNameAndResolution(currentName)
                    channels.add(
                        Channel(
                            name = baseName,
                            logoUrl = currentLogoUrl,
                            groupTitle = "", // We will guess this later
                            variants = listOf(ChannelVariant(url = line, resolution = resolution))
                        )
                    )
                }
                currentName = ""
                currentLogoUrl = ""
            }
            line = reader.readLine()
        }

        // Variant Merging
        val groupedByName = channels.groupBy { it.name }
        val mergedChannels = groupedByName.map { (name, channelList) ->
            val first = channelList.first()
            val allVariants = channelList.flatMap { it.variants }.sortedByDescending { parseResInt(it.resolution) }
            val logo = first.logoUrl.ifEmpty { channelList.find { it.logoUrl.isNotEmpty() }?.logoUrl ?: "" }
            
            val group = guessGroup(name)
            val score = engagementStore.getScore(name)

            Channel(
                name = name,
                logoUrl = logo,
                groupTitle = group,
                variants = allVariants,
                engagementScore = score
            )
        }

        // Apply folder and channel default + engagement sorting
        val groups = mergedChannels.groupBy { it.groupTitle }
        
        return groups.map { (title, list) ->
            // Sort channels inside folder: Engagement > Flagship > Name
            val sortedChannels = list.sortedWith(compareByDescending<Channel> { it.engagementScore }
                .thenByDescending { getChannelBasePriority(it.name) }
                .thenBy { it.name })
                
            val folderScore = sortedChannels.map { it.engagementScore }.sum()
            
            ChannelGroup(
                title = title, 
                channels = sortedChannels,
                score = folderScore
            )
        }.sortedWith(compareByDescending<ChannelGroup> { it.score }
            .thenBy { getFolderBasePriority(it.title) }
            .thenBy { it.title })
    }

    private fun extractAttribute(line: String, attribute: String): String {
        val regex = "$attribute=\"([^\"]*)\"".toRegex()
        val matchResult = regex.find(line)
        return matchResult?.groups?.get(1)?.value ?: ""
    }

    private fun parseNameAndResolution(rawName: String): Pair<String, String> {
        // Match [720p], (1080p), (FHD), (SD), (HD)
        val regex = "(\\[.*?\\]|\\(.*?\\)|\\bFHD\\b|\\bHD\\b|\\bSD\\b)".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(rawName)
        if (match != null) {
            val resolution = match.value.replace("(", "").replace(")", "").replace("[", "").replace("]", "")
            val baseName = rawName.replace(match.value, "").trim()
            return Pair(baseName, resolution)
        }
        return Pair(rawName, "unknown")
    }

    private fun parseResInt(resString: String): Int {
        val clean = resString.lowercase().replace("p", "").trim()
        if (clean == "hd" || clean == "fhd") return 1080
        if (clean == "sd") return 480
        if (clean == "4k") return 2160
        return clean.toIntOrNull() ?: 0
    }

    private fun guessGroup(channelName: String): String {
        val upper = channelName.uppercase()
        return when {
            upper.startsWith("STAR ") || upper.startsWith("STAR") -> "Star"
            upper.startsWith("SONY") -> "Sony"
            upper.startsWith("ZEE") -> "Zee"
            upper.startsWith("COLORS") -> "Colors"
            upper.startsWith("DD ") || upper.startsWith("DD") -> "DD"
            upper.startsWith("SUN ") || upper.startsWith("SUN") -> "Sun"
            upper.startsWith("NEWS18") -> "News18"
            upper.startsWith("ABP") -> "ABP"
            else -> {
                // If it contains these words, maybe general grouping
                if (upper.contains("NEWS") || upper.contains("AAJ TAK") || upper.contains("NDTV") || upper.contains("REPUBLIC")) return "News"
                if (upper.contains("SPORTS") || upper.contains("TEN ") || upper.contains("ESPN")) return "Sports"
                if (upper.contains("MOVIE") || upper.contains("CINEMA") || upper.contains("PICTURES") || upper.contains("GOLD")) return "Movies"
                if (upper.contains("MUSIC") || upper.contains("MTV") || upper.contains("VH1") || upper.contains("9XM") || upper.contains("9X ")) return "Music"
                if (upper.contains("KIDS") || upper.contains("CARTOON") || upper.contains("POGO") || upper.contains("DISNEY") || upper.contains("NICK")) return "Kids"
                if (upper.contains("DISCOVERY") || upper.contains("ANIMAL PLANET") || upper.contains("HISTORY") || upper.contains("NAT GEO")) return "Infotainment"
                
                // Fallback to first word
                upper.split(" ").firstOrNull() ?: "Others"
            }
        }
    }

    private fun getFolderBasePriority(folder: String): Int {
        // Star > Sony > Zee > Colors > DD > Sun > News18 > ABP > Others
        return when (folder.uppercase()) {
            "STAR" -> 1
            "SONY" -> 2
            "ZEE" -> 3
            "COLORS" -> 4
            "DD" -> 5
            "SUN" -> 6
            "NEWS18" -> 7
            "ABP" -> 8
            "OTHERS" -> 99
            else -> 10
        }
    }

    private fun getChannelBasePriority(channelName: String): Int {
        // Give slight bump to Flagship / General / Sports
        val upper = channelName.uppercase()
        if (upper.contains("SPORTS") || upper.contains("PLUS") || upper.contains("TV")) return 10
        if (upper.contains("NEWS") || upper.contains("REGIONAL")) return 0
        return 5
    }
}