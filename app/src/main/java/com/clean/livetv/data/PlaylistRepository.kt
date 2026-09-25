package com.clean.livetv.data

import android.content.Context
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
                .build()
            
            var response = chain.proceed(request)
            var tryCount = 0
            while (!response.isSuccessful && tryCount < 3) {
                tryCount++
                response.close()
                try {
                    Thread.sleep((1000 * tryCount).toLong())
                } catch (ignored: Exception) {}
                response = chain.proceed(request)
            }
            response
        }
        .build()

    private val _channelsFlow = MutableStateFlow<List<ChannelGroup>>(emptyList())
    val channelsFlow: StateFlow<List<ChannelGroup>> = _channelsFlow

    private val playlistUrl = "https://iptv-org.github.io/iptv/countries/in.m3u"
    private val cacheFile = File(context.cacheDir, "channels_cache.json")
    private val prefs = context.getSharedPreferences("playlist_prefs", Context.MODE_PRIVATE)

    suspend fun loadAndSync() = withContext(Dispatchers.IO) {
        // 1. Boot Instantly: load from cache
        loadFromCache()

        // 2. Background Check
        val lastEtag = prefs.getString("etag", "") ?: ""
        val lastSync = prefs.getLong("last_sync", 0L)
        val ttlMillis = 6 * 60 * 60 * 1000L // 6 hours

        try {
            val isExpired = System.currentTimeMillis() - lastSync > ttlMillis
            val requestBuilder = Request.Builder().url(playlistUrl).head()
            if (!isExpired && lastEtag.isNotEmpty()) {
                requestBuilder.header("If-None-Match", lastEtag)
            }
            
            val headResponse = client.newCall(requestBuilder.build()).execute()
            
            // 3. Delta Sync
            if (headResponse.code != 304 || isExpired) {
                // Modified or expired, fetch it fully
                val newEtag = headResponse.header("ETag") ?: ""
                headResponse.close()
                fetchAndParseM3uWithRetry(newEtag)
            } else {
                headResponse.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If head request fails due to no internet, try to fetch anyway or fallback to cache
            fetchAndParseM3uWithRetry("")
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
                delay(2000L * attempts) // Exponential backoff 2s, 4s
            }
        }
    }

    private fun loadFromCache() {
        if (cacheFile.exists()) {
            try {
                val jsonStr = cacheFile.readText()
                val cachedGroups = Json.decodeFromString<List<ChannelGroup>>(jsonStr)
                // We re-sort from cache on cold boot to ensure engagement score is applied
                // because engagement score could have updated since last json caching.
                val updatedGroups = cachedGroups.map { group ->
                    group.copy(
                        channels = group.channels.map { ch ->
                            ch.copy(engagementScore = engagementStore.getScore(ch.name))
                        }.sortedWith(compareByDescending<Channel> { it.engagementScore }.thenBy { it.name })
                    )
                }.map { group ->
                    group.copy(score = group.channels.map { it.engagementScore }.sum())
                }.sortedWith(compareByDescending<ChannelGroup> { it.score }
                    .thenBy { getFolderBasePriority(it.title) }
                    .thenBy { it.title })

                _channelsFlow.value = updatedGroups
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun fetchAndParseM3u(newEtag: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(playlistUrl).get().build()
        val response = client.newCall(request).execute()
        
        if (response.isSuccessful) {
            response.body?.byteStream()?.use { stream ->
                val groups = M3uParser.parse(stream, engagementStore)
                
                // 4. Silent UI Update
                _channelsFlow.value = groups

                // Save cache
                val jsonStr = Json.encodeToString(groups)
                cacheFile.writeText(jsonStr)

                // Update prefs
                if (newEtag.isNotEmpty()) {
                    prefs.edit()
                        .putString("etag", newEtag)
                        .putLong("last_sync", System.currentTimeMillis())
                        .apply()
                }
            }
        } else {
            throw Exception("HTTP Failed: ${response.code}")
        }
    }
    
    private fun getFolderBasePriority(folder: String): Int {
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
}