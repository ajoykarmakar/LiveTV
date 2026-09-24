package com.clean.livetv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class PlaylistRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _channelsFlow = MutableStateFlow<List<ChannelGroup>>(emptyList())
    val channelsFlow: StateFlow<List<ChannelGroup>> = _channelsFlow

    private val playlistUrl = "https://raw.githubusercontent.com/iptv-org/iptv/refs/heads/master/streams/in.m3u"
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
                fetchAndParseM3u(newEtag)
            } else {
                headResponse.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If network fails, we just keep the cached version which is already loaded.
        }
    }

    private fun loadFromCache() {
        if (cacheFile.exists()) {
            try {
                val jsonStr = cacheFile.readText()
                val cachedGroups = Json.decodeFromString<List<ChannelGroup>>(jsonStr)
                _channelsFlow.value = cachedGroups
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun fetchAndParseM3u(newEtag: String) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(playlistUrl).get().build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { stream ->
                    val groups = M3uParser.parse(stream)
                    
                    // 4. Silent UI Update
                    _channelsFlow.value = groups

                    // Save cache
                    val jsonStr = Json.encodeToString(groups)
                    cacheFile.writeText(jsonStr)

                    // Update prefs
                    prefs.edit()
                        .putString("etag", newEtag)
                        .putLong("last_sync", System.currentTimeMillis())
                        .apply()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}