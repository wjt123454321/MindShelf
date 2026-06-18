package com.example.mindshelf.data.repository

import com.example.mindshelf.data.local.AiPreferences
import com.example.mindshelf.data.local.SecureStore
import com.example.mindshelf.data.local.dao.AiProviderDao
import com.example.mindshelf.data.local.entity.AiProviderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val hasApiKey: Boolean,
)

@Singleton
class AiProviderRepository @Inject constructor(
    private val dao: AiProviderDao,
    private val secureStore: SecureStore,
    private val aiPreferences: AiPreferences,
) {
    fun observeProviders(): Flow<List<AiProvider>> =
        dao.observeAll().map { list ->
            list.map { it.toModel(secureStore.getApiKey(it.id) != null) }
        }

    suspend fun listProviders(): List<AiProvider> =
        dao.getAll().map { it.toModel(secureStore.getApiKey(it.id) != null) }

    suspend fun getProvider(id: String): AiProvider? =
        dao.getById(id)?.toModel(secureStore.getApiKey(id) != null)

    suspend fun addProvider(
        name: String,
        baseUrl: String,
        model: String,
        apiKey: String,
        setDefault: Boolean = true,
    ): AiProvider {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        if (setDefault) dao.clearDefault()
        val entity = AiProviderEntity(
            id = id,
            name = name.trim(),
            baseUrl = baseUrl.trim().trimEnd('/'),
            model = model.trim(),
            isDefault = setDefault,
            createdAt = now,
        )
        dao.upsert(entity)
        secureStore.saveApiKey(id, apiKey.trim())
        if (setDefault) {
            aiPreferences.setChannel(id)
        }
        return entity.toModel(true)
    }

    suspend fun deleteProvider(id: String) {
        dao.delete(id)
        secureStore.deleteApiKey(id)
        if (aiPreferences.getChannel() == id) {
            aiPreferences.setChannel(AiPreferences.CHANNEL_BUILTIN)
        }
    }

    suspend fun setActiveChannel(channel: String) {
        aiPreferences.setChannel(channel)
    }

    suspend fun getActiveChannel(): String = aiPreferences.getChannel()

    suspend fun getApiKey(providerId: String): String? = secureStore.getApiKey(providerId)

    private fun AiProviderEntity.toModel(hasKey: Boolean) = AiProvider(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        isDefault = isDefault,
        createdAt = createdAt,
        hasApiKey = hasKey,
    )
}
