package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.reelboxPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reelbox_preferences",
)

/**
 * Persists favourite media IDs and lightweight local app flags using DataStore Preferences
 * so favourites survive closing and reopening the app.
 */
class FavouritesRepository(context: Context) {

    private val dataStore = context.applicationContext.reelboxPreferencesDataStore

    val favouriteIds: Flow<Set<Long>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[FAVOURITE_IDS_KEY]
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                ?: emptySet()
        }

    val hasRequestedPermission: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[HAS_REQUESTED_PERMISSION_KEY] ?: false
        }

    val isMuted: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[IS_MUTED_KEY] ?: false
        }

    suspend fun toggleFavourite(mediaId: Long) {
        val key = mediaId.toString()
        dataStore.edit { preferences ->
            val current = preferences[FAVOURITE_IDS_KEY].orEmpty().toMutableSet()
            if (!current.add(key)) {
                current.remove(key)
            }
            preferences[FAVOURITE_IDS_KEY] = current
        }
    }

    suspend fun setFavourite(mediaId: Long, isFavourite: Boolean) {
        val key = mediaId.toString()
        dataStore.edit { preferences ->
            val current = preferences[FAVOURITE_IDS_KEY].orEmpty().toMutableSet()
            if (isFavourite) {
                current.add(key)
            } else {
                current.remove(key)
            }
            preferences[FAVOURITE_IDS_KEY] = current
        }
    }

    suspend fun pruneMissingIds(validIds: Set<Long>) {
        dataStore.edit { preferences ->
            val current = preferences[FAVOURITE_IDS_KEY] ?: return@edit
            val validKeys = validIds.mapTo(HashSet(validIds.size)) { it.toString() }
            val filtered = current.filterTo(LinkedHashSet()) { it in validKeys }
            if (filtered.size != current.size) {
                preferences[FAVOURITE_IDS_KEY] = filtered
            }
        }
    }

    suspend fun setMuted(muted: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_MUTED_KEY] = muted
        }
    }

    suspend fun markPermissionRequested() {
        dataStore.edit { preferences ->
            preferences[HAS_REQUESTED_PERMISSION_KEY] = true
        }
    }

    private companion object {
        val FAVOURITE_IDS_KEY = stringSetPreferencesKey("favourite_media_ids")
        val HAS_REQUESTED_PERMISSION_KEY = booleanPreferencesKey("has_requested_media_permission")
        val IS_MUTED_KEY = booleanPreferencesKey("feed_is_muted")
    }
}
