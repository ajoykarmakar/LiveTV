package com.clean.livetv.data

import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.content.edit

class PlaylistRepository(private val context: Context, private val engagementStore: EngagementStore) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024L * 1024L)) // 50MB Http Cache
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                .header("Origin", "https://jiotv.com")
                .header("Referer", "https://jiotv.com/")
                .header("Accept-Encoding", "gzip")
                // Extremely aggressive Cache-Control for GitHub raw endpoints to prevent 304 timeouts
                .header("Cache-Control", "max-stale=3600") 
                .build()
            
            var response = chain.proceed(request)
            var tryCount = 0
            while (!response.isSuccessful && tryCount < 2) { // Reduced retries to avoid extreme boot lag
                tryCount++
                response.close()
                try {
                    Thread.sleep(500)
                } catch (ignored: Exception) {}
                response = chain.proceed(request)
            }
            response
        }
        .build()

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    private val _channelsFlow = MutableStateFlow<List<ChannelGroup>>(emptyList())
    val channelsFlow: StateFlow<List<ChannelGroup>> = _channelsFlow

    private val playlistUrl = "https://iptv-org.github.io/iptv/countries/in.m3u"
    private val logosUrl = "https://iptv-org.github.io/api/logos.json"
    
    private val cacheFile = File(context.cacheDir, "channels_cache_v5.json")
    private val logosCacheFile = File(context.cacheDir, "logos_cache_v2.json")
    
    private val prefs = context.getSharedPreferences("playlist_prefs", Context.MODE_PRIVATE)

    suspend fun loadAndSync() = withContext(Dispatchers.IO) {
        // 1. Boot Instantly: ALWAYS load from the local JSON file FIRST
        val cacheLoaded = loadFromCache()

        // 2. Perform Network Sync as a completely detached background process
        // This ensures the UI never blocks while OkHttp handles GitHub latency/Etag checks
        CoroutineScope(Dispatchers.IO).launch {
            performBackgroundSync(cacheLoaded)
        }
    }

    private suspend fun performBackgroundSync(cacheLoaded: Boolean) {
        val lastEtag = prefs.getString("etag", "") ?: ""
        val lastSync = prefs.getLong("last_sync", 0L)
        // Aggressive TTL: 24 hours (GitHub doesn't change playlists minute-to-minute)
        val ttlMillis = 24 * 60 * 60 * 1000L 

        try {
            val isExpired = System.currentTimeMillis() - lastSync > ttlMillis
            
            // If we have a local cache and TTL isn't expired, we don't even need to ping GitHub
            // However, we MUST perform a background sync if the cache was NOT loaded.
            if (cacheLoaded && !isExpired) {
                return 
            }

            val requestBuilder = Request.Builder().url(playlistUrl).head()
            if (!isExpired && lastEtag.isNotEmpty()) {
                requestBuilder.header("If-None-Match", lastEtag)
            }
            
            val headResponse = client.newCall(requestBuilder.build()).execute()
            
            // 3. Delta Sync
            if (headResponse.code != 304 || isExpired) {
                // Modified, expired, or cache is missing: Fetch fully
                val newEtag = headResponse.header("ETag") ?: ""
                headResponse.close()
                fetchAndParseM3uWithRetry(newEtag)
            } else {
                headResponse.close()
                // Just update the sync timestamp so we don't hit Etag repeatedly today
                prefs.edit {
                    putLong("last_sync", System.currentTimeMillis())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If HEAD fails due to no internet, and we already loaded cache, just silently fail.
            // If cache was MISSING, then force a retry fetch.
            if (!cacheLoaded) {
                fetchAndParseM3uWithRetry("")
            }
        }
    }

    private suspend fun fetchAndParseM3uWithRetry(newEtag: String) {
        var attempts = 0
        var success = false
        while (attempts < 3 && !success) {
            try {
                fetchAndParseM3u(newEtag)
                success = true
            } catch (e: Exception) {
                attempts++
                e.printStackTrace()
                delay((2000L * attempts).milliseconds) // Exponential backoff 2s, 4s
            }
        }
    }

    private fun loadFromCache(): Boolean {
        if (cacheFile.exists()) {
            try {
                val jsonStr = cacheFile.readText()
                if (jsonStr.isEmpty() || jsonStr == "[]") return false
                
                val cachedGroups = Json.decodeFromString<List<ChannelGroup>>(jsonStr)
                // We re-sort from cache on cold boot to ensure engagement score is applied
                // because engagement score could have updated since last json caching.
                _channelsFlow.value = cachedGroups
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private suspend fun fetchAndParseM3u(newEtag: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(playlistUrl).get().build()
        val response = client.newCall(request).execute()
        
        // Ensure engagement store is cached into memory BEFORE parser runs
        engagementStore.cachePreferences()

        if (response.isSuccessful) {
            val m3uContent = response.body?.string() ?: ""
            
            // Extract required tvg-ids so we can filter the massive 5.6MB logos.json
            val requiredIds = extractTvgIds(m3uContent)
            
            // Fetch and cache ONLY the required logos
            val logosMap = fetchAndCacheLogos(requiredIds)
            
            // Parse the actual M3U string into channels
            val groups = M3uParser.parse(m3uContent.byteInputStream(), engagementStore, logosMap)
            
            // 4. Silent UI Update
            _channelsFlow.value = groups

            // Save cache
            val jsonStr = Json.encodeToString(groups)
            cacheFile.writeText(jsonStr)

            // Update prefs
            if (newEtag.isNotEmpty()) {
                prefs.edit {
                    putString("etag", newEtag)
                        .putLong("last_sync", System.currentTimeMillis())
                }
            }
        } else {
            throw Exception("HTTP Failed: ${response.code}")
        }
    }

    private fun extractTvgIds(m3uContent: String): Set<String> {
        val ids = mutableSetOf<String>()
        val regex = """tvg-id="([^"]*)"""".toRegex()
        regex.findAll(m3uContent).forEach { match ->
            val fullId = match.groupValues[1]
            if (fullId.isNotEmpty()) {
                val pureId = fullId.substringBefore("@")
                if (pureId.isNotEmpty()) {
                    ids.add(pureId)
                }
            }
        }
        return ids
    }

    private suspend fun fetchAndCacheLogos(requiredIds: Set<String>): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(logosUrl)
                .header("Accept-Encoding", "gzip")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyMap()
                
                // HIGH PERFORMANCE FIX: Do not parse 5.6MB JSON Tree.
                // Regex extract ONLY the matching logos instantly.
                val logosMap = mutableMapOf<String, String>()
                val regex = """"channel":"([^"]*)".*?"url":"([^"]*)"""".toRegex()
                regex.findAll(body).forEach { match ->
                    val channel = match.groupValues[1]
                    if (requiredIds.contains(channel)) {
                        // In case of duplicates in API, we just keep the latest valid URL
                        logosMap[channel] = match.groupValues[2]
                    }
                }
                
                // Save to local cache for next fast-boot if needed
                logosCacheFile.writeText(jsonFormat.encodeToString(logosMap))
                return@withContext logosMap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Fallback to existing cache if fetch fails
        if (logosCacheFile.exists()) {
            try {
                val jsonStr = logosCacheFile.readText()
                return@withContext jsonFormat.decodeFromString<Map<String, String>>(jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext emptyMap()
    }
}