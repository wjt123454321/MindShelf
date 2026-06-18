package com.example.mindshelf.data.repository

import com.example.mindshelf.data.local.dao.PageDao
import com.example.mindshelf.data.local.entity.SyncStatus
import com.example.mindshelf.data.local.jsonToMap
import com.example.mindshelf.data.local.mapToJson
import com.example.mindshelf.data.local.toDto
import com.example.mindshelf.data.local.toEntity
import com.example.mindshelf.data.page.PageSchemaValidator
import com.example.mindshelf.data.remote.MindShelfApi
import com.example.mindshelf.data.remote.dto.CreatePageRequest
import com.example.mindshelf.data.remote.dto.CustomPageDto
import com.example.mindshelf.data.remote.dto.UpdatePageRequest
import com.example.mindshelf.data.sync.SyncCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageRepository @Inject constructor(
    private val api: MindShelfApi,
    private val pageDao: PageDao,
    private val syncCoordinator: SyncCoordinator,
) {
    fun observePages(): Flow<List<CustomPageDto>> =
        pageDao.observeActive().map { pages -> pages.map { it.toDto() } }

    fun observePinnedPage(): Flow<CustomPageDto?> =
        pageDao.observePinned().map { it?.toDto() }

    suspend fun listActivePages(): List<CustomPageDto> =
        pageDao.getAllActive().map { it.toDto() }

    suspend fun searchPages(query: String, pageId: String? = null): Map<String, Any?> {
        if (!pageId.isNullOrBlank()) {
            val page = get(pageId) ?: return mapOf("error" to "页面不存在")
            return mapOf("page" to pageToSearchMap(page))
        }
        var pages = listActivePages()
        val q = query.trim()
        if (q.isNotEmpty()) {
            pages = pages.filter { it.name.contains(q, ignoreCase = true) }
        }
        return mapOf(
            "items" to pages.take(20).map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "pinned" to it.pinned,
                    "updated_at" to it.updatedAt,
                )
            },
        )
    }

    suspend fun migrateContentBindings(page: CustomPageDto): CustomPageDto {
        @Suppress("UNCHECKED_CAST")
        val schema = page.schemaJson.toMutableMap()
        val root = schema["root"] as? Map<String, Any?> ?: return page
        val children = (root["children"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }?.toMutableList()
            ?: return page
        val bindings = page.dataBindings.toMutableMap()
        var changed = false
        children.forEachIndexed { index, child ->
            if (child["type"]?.toString() != "TextBlock") return@forEachIndexed
            val props = child["props"] as? Map<String, Any?> ?: return@forEachIndexed
            if (props["binding"]?.toString()?.isNotBlank() == true) return@forEachIndexed
            val text = props["text"]?.toString()?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            var key = if (index == 0) "intro" else "text_$index"
            while (key in bindings) key = "${key}_${UUID.randomUUID().toString().take(4)}"
            bindings[key] = mapOf("kind" to "text", "text" to text)
            children[index] = mapOf("type" to "TextBlock", "props" to mapOf("binding" to key))
            changed = true
        }
        if (!changed) return page
        val newRoot = root.toMutableMap().apply { put("children", children) }
        schema["root"] = newRoot
        return update(id = page.id, schemaJson = schema, dataBindings = bindings)
    }

    private fun pageToSearchMap(page: CustomPageDto): Map<String, Any?> = mapOf(
        "id" to page.id,
        "name" to page.name,
        "schema_json" to page.schemaJson,
        "data_bindings" to page.dataBindings,
        "pinned" to page.pinned,
        "updated_at" to page.updatedAt,
    )

    suspend fun get(id: String): CustomPageDto? {
        val entity = pageDao.getById(id) ?: return if (syncCoordinator.shouldWriteRemote()) {
            try {
                api.getPage(id).data.also { saveRemote(it) }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        return entity.toDto()
    }

    suspend fun create(
        name: String,
        schemaJson: Map<String, Any?> = PageSchemaValidator.defaultSchema(),
        dataBindings: Map<String, Any?> = emptyMap(),
        pinned: Boolean = false,
        id: String? = null,
    ): CustomPageDto {
        PageSchemaValidator.validateSchema(schemaJson)?.let { error(it) }
        PageSchemaValidator.validateBindings(dataBindings)?.let { error(it) }
        val pageId = id ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        if (pinned) pageDao.clearOtherPins(pageId)
        val entity = com.example.mindshelf.data.local.entity.CustomPageEntity(
            id = pageId,
            name = name,
            schemaJson = mapToJson(schemaJson),
            dataBindings = mapToJson(dataBindings),
            pinned = pinned,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_CREATE,
        )
        pageDao.upsert(entity)
        if (syncCoordinator.shouldWriteRemote()) {
            pushCreate(entity)
        }
        return pageDao.getById(pageId)?.toDto() ?: entity.toDto()
    }

    suspend fun update(
        id: String,
        name: String? = null,
        schemaJson: Map<String, Any?>? = null,
        dataBindings: Map<String, Any?>? = null,
        pinned: Boolean? = null,
    ): CustomPageDto {
        val existing = pageDao.getById(id) ?: error("页面不存在")
        schemaJson?.let { PageSchemaValidator.validateSchema(it)?.let { msg -> error(msg) } }
        dataBindings?.let { PageSchemaValidator.validateBindings(it)?.let { msg -> error(msg) } }
        val now = System.currentTimeMillis()
        if (pinned == true) pageDao.clearOtherPins(id)
        val entity = existing.copy(
            name = name ?: existing.name,
            schemaJson = schemaJson?.let { mapToJson(it) } ?: existing.schemaJson,
            dataBindings = dataBindings?.let { mapToJson(it) } ?: existing.dataBindings,
            pinned = pinned ?: existing.pinned,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        pageDao.upsert(entity)
        if (syncCoordinator.shouldWriteRemote()) {
            pushUpdate(entity)
        }
        return pageDao.getById(id)?.toDto() ?: entity.toDto()
    }

    suspend fun updateBinding(id: String, bindingKey: String, binding: Map<String, Any?>): CustomPageDto {
        val existing = pageDao.getById(id) ?: error("页面不存在")
        val bindings = jsonToMap(existing.dataBindings).toMutableMap()
        bindings[bindingKey] = binding
        PageSchemaValidator.validateBindings(bindings)?.let { error(it) }
        return update(id = id, dataBindings = bindings)
    }

    suspend fun setPinned(id: String, pinned: Boolean): CustomPageDto =
        update(id = id, pinned = pinned)

    suspend fun delete(id: String) {
        val now = System.currentTimeMillis()
        pageDao.markDeleted(id, now, now, SyncStatus.PENDING_DELETE)
        if (syncCoordinator.shouldWriteRemote()) {
            try {
                api.deletePage(id)
                pageDao.markDeleted(id, now, now, SyncStatus.SYNCED)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun restoreFromTrash(id: String) {
        val now = System.currentTimeMillis()
        val status = if (syncCoordinator.shouldWriteRemote()) SyncStatus.SYNCED else SyncStatus.PENDING_UPDATE
        pageDao.restore(id, now, status)
        if (syncCoordinator.shouldWriteRemote()) {
            runCatching {
                api.restoreTrash(
                    com.example.mindshelf.data.remote.dto.TrashRestoreRequest("page", id),
                )
            }
        }
    }

    suspend fun purgeLocal(id: String) {
        pageDao.purge(id)
    }

    private suspend fun saveRemote(dto: CustomPageDto, status: SyncStatus = SyncStatus.SYNCED) {
        pageDao.upsert(dto.toEntity(status))
    }

    private suspend fun pushCreate(entity: com.example.mindshelf.data.local.entity.CustomPageEntity): Boolean {
        return try {
            val remote = api.createPage(
                CreatePageRequest(
                    id = entity.id,
                    name = entity.name,
                    schemaJson = jsonToMap(entity.schemaJson),
                    dataBindings = jsonToMap(entity.dataBindings),
                    pinned = entity.pinned,
                ),
            ).data
            saveRemote(remote)
            true
        } catch (_: HttpException) {
            fetchAndSaveRemote(entity.id)
        } catch (_: Exception) {
            fetchAndSaveRemote(entity.id)
        }
    }

    private suspend fun pushUpdate(entity: com.example.mindshelf.data.local.entity.CustomPageEntity): Boolean {
        return try {
            val remote = api.updatePage(
                entity.id,
                UpdatePageRequest(
                    name = entity.name,
                    schemaJson = jsonToMap(entity.schemaJson),
                    dataBindings = jsonToMap(entity.dataBindings),
                    pinned = entity.pinned,
                ),
            ).data
            saveRemote(remote)
            true
        } catch (_: HttpException) {
            fetchAndSaveRemote(entity.id)
        } catch (_: Exception) {
            fetchAndSaveRemote(entity.id)
        }
    }

    private suspend fun fetchAndSaveRemote(id: String): Boolean =
        runCatching {
            api.getPage(id).data.also { saveRemote(it) }
            true
        }.getOrDefault(false)

    suspend fun markDeletedFromServer(id: String) {
        val now = System.currentTimeMillis()
        pageDao.markDeleted(id, now, now, SyncStatus.SYNCED)
    }

    suspend fun applyFromToolResult(result: Map<String, Any?>) {
        val pageId = result["page_id"]?.toString() ?: return
        val name = result["name"]?.toString() ?: return
        @Suppress("UNCHECKED_CAST")
        val schema = result["schema_json"] as? Map<String, Any?> ?: PageSchemaValidator.defaultSchema()
        @Suppress("UNCHECKED_CAST")
        val bindings = result["data_bindings"] as? Map<String, Any?> ?: emptyMap()
        val pinned = result["pinned"] as? Boolean ?: false
        val updatedAt = (result["updated_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val status = if (syncCoordinator.shouldWriteRemote()) SyncStatus.SYNCED else SyncStatus.PENDING_UPDATE
        if (pinned) pageDao.clearOtherPins(pageId)
        saveRemote(
            CustomPageDto(
                id = pageId,
                name = name,
                schemaJson = schema,
                dataBindings = bindings,
                pinned = pinned,
                createdAt = pageDao.getById(pageId)?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
            ),
            status,
        )
    }
}
