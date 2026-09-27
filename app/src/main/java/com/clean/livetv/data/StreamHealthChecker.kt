package com.clean.livetv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

enum class StreamStatus {
    CHECKING,
    ONLINE,
    OFFLINE
}

object StreamHealthChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    // Cache results so we don't spam requests when scrolling up and down
    private val statusCache = ConcurrentHashMap<String, StreamStatus>()

    suspend fun checkChannel(channel: Channel): StreamStatus = withContext(Dispatchers.IO) {
        val cached = statusCache[channel.name]
        if (cached != null) return@withContext cached

        for (variant in channel.variants) {
            try {
                val request = Request.Builder()
                    .url(variant.url)
                    .head() // HEAD is faster than GET
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                    .header("Origin", "https://jiotv.com")
                    .header("Referer", "https://jiotv.com/")
                    .header("Accept", "*/*")
                    .build()
                client.newCall(request).execute().use { response ->
                    val code = response.code
                    if (response.isSuccessful || response.isRedirect || code == 405 || code == 400) {
                        statusCache[channel.name] = StreamStatus.ONLINE
                        return@withContext StreamStatus.ONLINE
                    }
                }
            } catch (e: Exception) {
                // If HEAD fails (e.g. server blocks HEAD), fallback to minimal GET
                try {
                    val getReq = Request.Builder()
                        .url(variant.url)
                        .get()
                        .header("Range", "bytes=0-100")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                        .header("Origin", "https://jiotv.com")
                        .header("Referer", "https://jiotv.com/")
                        .header("Accept", "*/*")
                        .build()
                    client.newCall(getReq).execute().use { response ->
                        if (response.isSuccessful || response.code == 206 || response.isRedirect) {
                            statusCache[channel.name] = StreamStatus.ONLINE
                            return@withContext StreamStatus.ONLINE
                        }
                    }
                } catch (e2: Exception) {
                    // Ignore and try next variant
                }
            }
        }
        statusCache[channel.name] = StreamStatus.OFFLINE
        return@withContext StreamStatus.OFFLINE
    }
}
