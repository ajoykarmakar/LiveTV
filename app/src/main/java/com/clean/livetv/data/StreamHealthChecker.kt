package com.clean.livetv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class StreamStatus {
    CHECKING,
    ONLINE,
    OFFLINE
}

object StreamHealthChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun checkChannel(channel: Channel): Boolean = withContext(Dispatchers.IO) {
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
                        // Some servers block HEAD with 405 or 400 but stream is still alive
                        return@withContext true
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
                            return@withContext true
                        }
                    }
                } catch (e2: Exception) {
                    // Ignore and try next variant
                }
            }
        }
        return@withContext false
    }
}