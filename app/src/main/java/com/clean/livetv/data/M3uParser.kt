package com.clean.livetv.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3uParser {
    fun parse(inputStream: InputStream, engagementStore: EngagementStore, logosMap: Map<String, String> = emptyMap()): List<ChannelGroup> {
        val channels = mutableListOf<Channel>()
        var currentName = ""
        var currentLogoUrl = ""
        var currentTvgId = ""
        
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String? = reader.readLine()

        while (line != null) {
            line = line.trim()
            if (line.startsWith("#EXTINF:")) {
                currentLogoUrl = extractAttribute(line, "tvg-logo")
                currentTvgId = extractAttribute(line, "tvg-id")
                
                val commaIndex = line.lastIndexOf(',')
                if (commaIndex != -1 && commaIndex + 1 < line.length) {
                    currentName = line.substring(commaIndex + 1).trim()
                } else {
                    currentName = "Unknown Channel"
                }
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                if (currentName.isNotEmpty()) {
                    val (baseName, resolution) = parseNameAndResolution(currentName)
                    
                    // Resolve logo using tvg-id mapping, fallback to parsed tvg-logo
                    var resolvedLogo = ""
                    if (currentTvgId.isNotEmpty()) {
                        val pureId = currentTvgId.substringBefore("@")
                        resolvedLogo = logosMap[pureId] ?: ""
                    }
                    if (resolvedLogo.isEmpty()) {
                        resolvedLogo = currentLogoUrl
                    }

                    channels.add(
                        Channel(
                            name = baseName,
                            logoUrl = resolvedLogo,
                            groupTitle = "", // We will guess this later
                            variants = listOf(ChannelVariant(url = line, resolution = resolution))
                        )
                    )
                }
                currentName = ""
                currentLogoUrl = ""
                currentTvgId = ""
            }
            line = reader.readLine()
        }

        // Variant Merging
        val groupedByName = channels.groupBy { it.name }
        val mergedChannels = groupedByName.map { (name, channelList) ->
            val allVariants = channelList.flatMap { it.variants }.sortedByDescending { parseResInt(it.resolution) }
            
            // Try to find a logo from the channel list, fallback to our massive GitHub repo, or keep empty
            val listLogo = channelList.find { it.logoUrl.isNotEmpty() }?.logoUrl ?: ""
            val logo = listLogo.ifEmpty { getGithubLogoUrl(name) ?: "" }
            
            val group = guessGroup(name)
            val score = engagementStore.getScore(name)

            // Calculate if the channel supports HD
            val hasHd = allVariants.any { parseResInt(it.resolution) >= 720 } || name.uppercase().contains("HD")

            Channel(
                name = name,
                logoUrl = logo,
                groupTitle = group,
                variants = allVariants,
                engagementScore = score,
                hasHdOrHigher = hasHd
            )
        }

        // Apply folder and channel default + engagement sorting
        val groups = mergedChannels.groupBy { it.groupTitle }
        
        val finalGroups = mutableListOf<ChannelGroup>()
        val otherChannelsList = mutableListOf<Channel>()

        groups.forEach { (title, list) ->
            if ((list.size == 1 && title != "Cartoon") || title == "Others" || title == "Other Channels") {
                // Single-channel folders go into "Other Channels"
                val updatedList = list.map { it.copy(groupTitle = "Other Channels") }
                otherChannelsList.addAll(updatedList)
            } else {
                // Sort channels inside folder: Engagement > Flagship > Name
                val sortedChannels = list.sortedWith(compareByDescending<Channel> { it.engagementScore }
                    .thenByDescending { getChannelBasePriority(it.name) }
                    .thenBy { it.name })
                    
                val folderScore = sortedChannels.map { it.engagementScore }.sum()
                
                finalGroups.add(ChannelGroup(
                    title = title, 
                    channels = sortedChannels,
                    score = folderScore
                ))
            }
        }

        if (otherChannelsList.isNotEmpty()) {
            val sortedOthers = otherChannelsList.sortedWith(compareByDescending<Channel> { it.engagementScore }
                .thenByDescending { getChannelBasePriority(it.name) }
                .thenBy { it.name })
            val othersScore = sortedOthers.map { it.engagementScore }.sum()
            finalGroups.add(ChannelGroup(
                title = "Other Channels",
                channels = sortedOthers,
                score = othersScore
            ))
        }

        return finalGroups.sortedWith(compareByDescending<ChannelGroup> { it.score }
            .thenBy { getFolderBasePriority(it.title) }
            .thenBy { it.title })
    }

    private fun extractAttribute(line: String, attribute: String): String {
        val regex = "$attribute=\"([^\"]*)\"".toRegex()
        val matchResult = regex.find(line)
        return matchResult?.groups?.get(1)?.value ?: ""
    }

    private fun parseNameAndResolution(rawName: String): Pair<String, String> {
        // First find the actual resolution
        val resRegex = "((?:1080|720|480|360|240|144)p|\\bFHD\\b|\\bHD\\b|\\bSD\\b|\\b4K\\b)".toRegex(RegexOption.IGNORE_CASE)
        val resMatch = resRegex.find(rawName)
        val resolution = resMatch?.groupValues?.get(1) ?: "unknown"

        // Remove EVERYTHING inside brackets or parentheses AND explicitly " (Stream Quality)"
        var baseName = rawName.replace(" (Stream Quality)", "", ignoreCase = true)
        baseName = baseName.replace("Stream Quality", "", ignoreCase = true)
        baseName = baseName.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")
        baseName = baseName.replace("()", "")
        
        // Remove standalone tags if they exist outside brackets
        baseName = baseName.replace(Regex("\\b(FHD|HD|SD|4K)\\b", RegexOption.IGNORE_CASE), "")
        
        // Clean up any remaining hyphens and spaces
        baseName = baseName.replace("-", "").replace(Regex("\\s+"), " ").trim()

        return Pair(baseName, resolution)
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
        
        // Prioritize Movies over brand networks so all movie channels go here
        if (upper.contains("MOVIE") || upper.contains("CINEMA") || 
            upper.contains("PICTURES") || upper.contains("GOLD") || 
            upper.contains("MAX") || upper.contains("CINEPLEX") || 
            upper.contains("BOLLYWOOD") || upper.contains("ACTION") || 
            upper.contains("FILM") || upper.contains("TALKIES")) {
            return "Movies"
        }

        // Prioritize Cartoons/Kids
        if (upper.contains("KIDS") || upper.contains("CARTOON") || upper.contains("POGO") || upper.contains("DISNEY") || upper.contains("NICK") || upper.contains("CHUTTI")) {
            return "Cartoon"
        }

        return when {
            upper.startsWith("STAR") -> "Star"
            upper.startsWith("SONY") -> "Sony"
            upper.startsWith("ZEE") -> "Zee"
            upper.startsWith("COLORS") -> "Colors"
            upper.startsWith("DD ") || upper.startsWith("DD") -> "DD"
            upper.startsWith("SUN ") || upper.startsWith("SUN") -> "Sun"
            upper.startsWith("NEWS18") -> "News18"
            upper.startsWith("ABP") -> "ABP"
            upper.startsWith("ASIANET") -> "Asianet"
            upper.startsWith("ETV") -> "ETV"
            upper.startsWith("B4U") -> "B4U"
            upper.startsWith("9X") -> "9X"
            upper.startsWith("POLIMER") -> "Polimer"
            upper.startsWith("KALAIGNAR") -> "Kalaignar"
            upper.startsWith("JAYA") -> "Jaya"
            else -> {
                // Genre-based grouping if no specific brand matches
                if (upper.contains("NEWS") || upper.contains("AAJ TAK") || upper.contains("NDTV") || upper.contains("REPUBLIC") || upper.contains("INDIA TV")) return "News"
                if (upper.contains("SPORTS") || upper.contains("TEN ") || upper.contains("ESPN")) return "Sports"
                if (upper.contains("MUSIC") || upper.contains("MTV") || upper.contains("VH1") || upper.contains("9XM") || upper.contains("ZING") || upper.contains("SANGEET")) return "Music"
                if (upper.contains("DISCOVERY") || upper.contains("ANIMAL PLANET") || upper.contains("HISTORY") || upper.contains("NAT GEO") || upper.contains("NATIONAL GEOGRAPHIC")) return "Infotainment"
                
                // Final Fallback: use the first word of the channel (Capitalized neatly)
                val firstWord = channelName.split(" ").firstOrNull()?.lowercase()?.replaceFirstChar { it.uppercase() }
                if (firstWord.isNullOrEmpty() || firstWord.length <= 2) "Other Channels" else firstWord
            }
        }
    }

    private fun getFolderBasePriority(folder: String): Int {
        // Custom order: Star > Sony > Zee > Colors > Music > Cartoon > DD > Sun > News18 > ABP > Others
        return when (folder.uppercase()) {
            "STAR" -> 1
            "SONY" -> 2
            "ZEE" -> 3
            "COLORS" -> 4
            "MOVIES" -> 5
            "MUSIC" -> 6
            "CARTOON" -> 7
            "NEWS" -> 8
            "DISCOVERY" -> 9
            "SUN" -> 11
            "ABP" -> 10
            "OTHER CHANNELS", "OTHERS" -> 99
            else -> 20
        }
    }

    private fun getChannelBasePriority(channelName: String): Int {
        // Give slight bump to Flagship / General / Sports
        val upper = channelName.uppercase()
        if (upper.contains("SPORTS") || upper.contains("PLUS") || upper.contains("TV")) return 10
        if (upper.contains("NEWS") || upper.contains("REGIONAL")) return 0
        return 5
    }

    private fun getGithubLogoUrl(channelName: String): String {
        // Generate the exact file name format expected by the GitHub Repo
        // Example: "Sony MAX" -> "Sony_MAX.png", "&TV" -> "&TV.png", "Aaj Tak HD" -> "Aaj_Tak_HD.png"
        val filename = channelName.trim().replace(" ", "_") + ".png"
        
        // We construct the raw GitHub content URL by appending it
        return "https://raw.githubusercontent.com/amjiddader/tv_logo/master/India/$filename"
    }
}