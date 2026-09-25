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
            
            // Override with Wikipedia Logo if available, else fallback to M3U logo
            val wikiLogo = getChannelWikipediaLogo(name)
            val logo = wikiLogo ?: first.logoUrl.ifEmpty { channelList.find { it.logoUrl.isNotEmpty() }?.logoUrl ?: "" }
            
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
        
        val finalGroups = mutableListOf<ChannelGroup>()
        val otherChannelsList = mutableListOf<Channel>()

        groups.forEach { (title, list) ->
            if ((list.size == 1 && title != "Cartoon") || title == "Others" || title == "Other Channels") {
                // Single-channel folders go into "Other Channels"
                val updatedList = list.map { it.copy(groupTitle = "Z Other Channels") }
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

    private fun getChannelWikipediaLogo(channelName: String): String? {
        // Match Indian television channels directly to their Wikipedia logos.
        // Data sourced from https://en.wikipedia.org/wiki/Lists_of_television_channels_in_India
        val cleanName = channelName.uppercase().trim()
        val filename = when {
            // Star Network
            cleanName == "STAR PLUS" -> "Star_Plus_logo.svg"
            cleanName == "STAR BHARAT" -> "Star_Bharat_logo.svg"
            cleanName == "STAR UTSAV" -> "Star_Utsav_logo.png"
            cleanName == "STAR GOLD" -> "Star_Gold_logo.svg"
            cleanName == "STAR MOVIES" -> "Star_Movies_logo.svg"
            cleanName == "STAR WORLD" -> "Star_World_logo.svg"
            cleanName == "STAR SPORTS 1" -> "Star_Sports_1_logo.svg"
            cleanName == "STAR SPORTS 2" -> "Star_Sports_2_logo.svg"
            cleanName == "STAR SPORTS 3" -> "Star_Sports_3_logo.svg"
            cleanName.startsWith("STAR SPORTS") -> "Star_Sports_Logo.svg"
            cleanName == "STAR VIJAY" -> "Star_Vijay_logo.svg"
            cleanName == "STAR JALSHA" -> "Star_Jalsha_logo.svg"
            cleanName == "STAR PRAVAH" -> "Star_Pravah_logo.svg"
            cleanName == "STAR MAA" -> "Star_Maa_logo.svg"
            cleanName == "STAR SUVARNA" -> "Star_Suvarna_logo.svg"
            cleanName == "ASIANET" -> "Asianet_logo.svg"

            // Sony Network
            cleanName == "SONY ENTERTAINMENT TELEVISION" || cleanName == "SET" -> "SET_India_logo.svg"
            cleanName == "SONY SAB" || cleanName == "SAB TV" -> "Sony_SAB_logo.svg"
            cleanName == "SONY PAL" -> "Sony_Pal_logo.svg"
            cleanName == "SONY MAX" -> "Sony_Max_logo.svg"
            cleanName == "SONY PIX" -> "Sony_PIX_logo.svg"
            cleanName == "SONY SIX" -> "Sony_SIX_logo.svg"
            cleanName.startsWith("SONY SPORTS") -> "Sony_Sports_Network_logo.svg"
            cleanName == "SONY YAY" || cleanName == "SONY YAY!" -> "Sony_Yay_logo.svg"

            // Zee Network
            cleanName == "ZEE TV" -> "Zee_TV_logo.svg"
            cleanName == "ZEE CINEMA" -> "Zee_Cinema_logo.svg"
            cleanName == "ZEE BOLLYWOOD" -> "Zee_Bollywood_logo.svg"
            cleanName == "ZEE ACTION" -> "Zee_Action_logo.svg"
            cleanName == "ZEE ANMOL" -> "Zee_Anmol_logo.svg"
            cleanName == "ZEE CAFE" -> "Zee_Cafe_logo.svg"
            cleanName == "ZEE ZEST" -> "Zee_Zest_logo.svg"
            cleanName == "ZEE NEWS" -> "Zee_News_logo.svg"
            cleanName == "ZEE BUSINESS" -> "Zee_Business_logo.svg"
            cleanName == "ZEE MARATHI" -> "Zee_Marathi_logo.svg"
            cleanName == "ZEE BANGLA" -> "Zee_Bangla_logo.svg"
            cleanName == "ZEE TAMIZH" -> "Zee_Tamil_logo.svg"
            cleanName == "ZEE TELUGU" -> "Zee_Telugu_logo.svg"
            cleanName == "ZEE KANNADA" -> "Zee_Kannada_logo.svg"
            cleanName == "ZEE KERALAM" -> "Zee_Keralam_logo.svg"
            cleanName == "&TV" || cleanName == "AND TV" -> "And_TV_logo.svg"
            cleanName == "&PICTURES" || cleanName == "AND PICTURES" -> "Andpictures_logo.svg"

            // Viacom18 / Colors
            cleanName == "COLORS" || cleanName == "COLORS TV" -> "Colors_TV_logo.svg"
            cleanName == "COLORS RISHTEY" -> "Colors_Rishtey_logo.svg"
            cleanName == "COLORS CINEPLEX" -> "Colors_Cineplex_logo.svg"
            cleanName == "COLORS INFINITY" -> "Colors_Infinity_logo.svg"
            cleanName == "MTV" || cleanName == "MTV INDIA" -> "MTV_India_logo.svg"
            cleanName == "MTV BEATS" -> "MTV_Beats_logo.svg"
            cleanName == "VH1" || cleanName == "VH1 INDIA" -> "VH1_India_logo.svg"
            cleanName == "NICKELODEON" || cleanName == "NICK" -> "Nickelodeon_logo.svg"
            cleanName == "NICK JR" || cleanName == "NICK JR." -> "Nick_Jr_logo.svg"
            cleanName == "SONIC" || cleanName == "SONIC NICKELODEON" -> "Sonic_Nickelodeon_logo.svg"
            cleanName == "COLORS MARATHI" -> "Colors_Marathi_logo.svg"
            cleanName == "COLORS KANNADA" -> "Colors_Kannada_logo.svg"
            cleanName == "COLORS BANGLA" -> "Colors_Bangla_logo.svg"
            cleanName == "COLORS GUJARATI" -> "Colors_Gujarati_logo.svg"

            // News & Regional
            cleanName == "AAJ TAK" -> "Aaj_Tak_logo.svg"
            cleanName == "INDIA TODAY" -> "India_Today_logo.svg"
            cleanName == "ABP NEWS" -> "ABP_News_logo.svg"
            cleanName == "ABP MAJHA" -> "ABP_Majha_logo.svg"
            cleanName == "ABP ANANDA" -> "ABP_Ananda_logo.svg"
            cleanName == "NEWS18 INDIA" -> "News18_India_logo.svg"
            cleanName == "CNN NEWS18" || cleanName == "CNN-NEWS18" -> "CNN-News18_logo.svg"
            cleanName == "INDIA TV" -> "India_TV_logo.svg"
            cleanName == "REPUBLIC TV" -> "Republic_TV_logo.svg"
            cleanName == "REPUBLIC BHARAT" -> "Republic_Bharat_logo.svg"
            cleanName == "NDTV 24X7" -> "NDTV_24x7_logo.svg"
            cleanName == "NDTV INDIA" -> "NDTV_India_logo.svg"
            cleanName == "TIMES NOW" -> "Times_Now_logo.svg"
            cleanName == "MIRROR NOW" -> "Mirror_Now_logo.svg"
            cleanName == "ET NOW" -> "ET_Now_logo.svg"
            cleanName == "PTC PUNJABI" -> "PTC_Punjabi_Logo.png"

            // Kids & Infotainment
            cleanName == "CARTOON NETWORK" -> "Cartoon_Network_logo.svg"
            cleanName == "POGO" || cleanName == "POGO TV" -> "Pogo_TV_logo.svg"
            cleanName == "DISNEY CHANNEL" -> "Disney_Channel_logo.svg"
            cleanName == "DISNEY JUNIOR" -> "Disney_Junior_logo.svg"
            cleanName == "HUNGAMA TV" -> "Hungama_TV_logo.svg"
            cleanName == "DISCOVERY CHANNEL" || cleanName == "DISCOVERY" -> "Discovery_Channel_logo.svg"
            cleanName == "ANIMAL PLANET" -> "Animal_Planet_logo.svg"
            cleanName == "TLC" -> "TLC_logo.svg"
            cleanName == "NATIONAL GEOGRAPHIC" || cleanName == "NAT GEO" -> "National_Geographic_Logo.svg"
            cleanName == "NAT GEO WILD" -> "Nat_Geo_Wild_logo.svg"
            cleanName == "HISTORY TV18" -> "History_TV18_logo.svg"

            // Sun & Southern
            cleanName == "SUN TV" -> "Sun_TV_logo.svg"
            cleanName == "KTV" -> "KTV_logo.svg"
            cleanName == "SUN MUSIC" -> "Sun_Music_logo.svg"
            cleanName == "SUN NEWS" -> "Sun_News_logo.svg"
            cleanName == "CHUTTI TV" -> "Chutti_TV_logo.svg"
            cleanName == "ADITHYA TV" -> "Adithya_TV_logo.svg"
            cleanName == "GEMINI TV" -> "Gemini_TV_logo.svg"
            cleanName == "UDAYA TV" -> "Udaya_TV_logo.svg"
            cleanName == "SURYA TV" -> "Surya_TV_logo.svg"
            cleanName.startsWith("ETV") -> "ETV_Network_logo.png"

            // Music & Movies
            cleanName == "9XM" -> "9XM_logo.png"
            cleanName == "9X JALWA" -> "9X_Jalwa_logo.png"
            cleanName == "B4U MOVIES" -> "B4U_Movies_logo.svg"
            cleanName == "B4U MUSIC" -> "B4U_Music_logo.svg"
            cleanName == "B4U KADAK" -> "B4U_Kadak_logo.svg"
            cleanName == "BINDASS" -> "Bindass_logo.svg"
            cleanName == "ZOOM" -> "Zoom_TV_logo.svg"

            // Doordarshan
            cleanName == "DD NATIONAL" -> "DD_National_logo.svg"
            cleanName == "DD NEWS" -> "DD_News_logo.svg"
            cleanName == "DD SPORTS" -> "DD_Sports_logo.svg"
            cleanName == "DD BHARATI" -> "DD_Bharati_logo.svg"
            cleanName == "DD KISAN" -> "DD_Kisan_logo.svg"
            cleanName.startsWith("DD ") -> "Doordarshan_Logo.svg"

            else -> null
        }
        
        return if (filename != null) {
            "https://en.wikipedia.org/wiki/Special:FilePath/$filename"
        } else {
            null
        }
    }
}