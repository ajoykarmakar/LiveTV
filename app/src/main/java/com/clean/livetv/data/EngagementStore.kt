package com.clean.livetv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "engagement")

class EngagementStore(private val context: Context) {
    fun getScore(channelName: String): Float {
        val openKey = intPreferencesKey("open_$channelName")
        val watchKey = longPreferencesKey("watch_$channelName")
        
        val (openCount, watchSeconds) = runBlocking {
            val prefs = context.dataStore.data.first()
            val o = prefs[openKey] ?: 0
            val w = prefs[watchKey] ?: 0L
            Pair(o, w)
        }
        
        val watchMinutes = watchSeconds / 60f
        return (openCount * 0.4f) + (watchMinutes * 0.6f)
    }

    suspend fun recordOpen(channelName: String) {
        val openKey = intPreferencesKey("open_$channelName")
        context.dataStore.edit { prefs ->
            val current = prefs[openKey] ?: 0
            prefs[openKey] = current + 1
        }
    }

    suspend fun recordWatchTime(channelName: String, seconds: Long) {
        if (seconds <= 0) return
        val watchKey = longPreferencesKey("watch_$channelName")
        context.dataStore.edit { prefs ->
            val current = prefs[watchKey] ?: 0L
            prefs[watchKey] = current + seconds
        }
    }
}
