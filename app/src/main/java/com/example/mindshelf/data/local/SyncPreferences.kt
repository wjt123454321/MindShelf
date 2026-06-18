package com.example.mindshelf.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncPrefs by preferencesDataStore("sync_settings")

/** 云同步开关与上次同步时间。 */
@Singleton
class SyncPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val enabledKey = booleanPreferencesKey("cloud_sync_enabled")
    private val lastSyncedKey = longPreferencesKey("last_synced_at")

    val cloudSyncEnabled: Flow<Boolean> =
        context.syncPrefs.data.map { it[enabledKey] ?: true }

    suspend fun isCloudSyncEnabled(): Boolean =
        context.syncPrefs.data.first()[enabledKey] ?: true

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.syncPrefs.edit { it[enabledKey] = enabled }
    }

    suspend fun getLastSyncedAt(): Long =
        context.syncPrefs.data.first()[lastSyncedKey] ?: 0L

    suspend fun setLastSyncedAt(timestamp: Long) {
        context.syncPrefs.edit { it[lastSyncedKey] = timestamp }
    }
}
