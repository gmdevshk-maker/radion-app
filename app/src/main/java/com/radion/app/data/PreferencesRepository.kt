package com.radion.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "radion_prefs")

/** 즐겨찾기 채널 목록과 마지막 재생 채널을 DataStore에 보존한다. */
class PreferencesRepository(private val context: Context) {

    val favorites: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_FAVORITES] ?: emptySet() }

    val lastChannelId: Flow<String?> =
        context.dataStore.data.map { it[KEY_LAST_CHANNEL] }

    suspend fun toggleFavorite(channelId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES] ?: emptySet()
            prefs[KEY_FAVORITES] =
                if (channelId in current) current - channelId else current + channelId
        }
    }

    suspend fun setLastChannel(channelId: String) {
        context.dataStore.edit { it[KEY_LAST_CHANNEL] = channelId }
    }

    companion object {
        private val KEY_FAVORITES = stringSetPreferencesKey("favorites")
        private val KEY_LAST_CHANNEL = stringPreferencesKey("last_channel")
    }
}
