package com.example.mindshelf.data.ai

import com.example.mindshelf.data.remote.MindShelfApi
import com.example.mindshelf.data.remote.dto.ToolContentSnapshot
import com.example.mindshelf.data.remote.dto.ToolPreview
import com.example.mindshelf.data.repository.KnowledgeRepository
import com.example.mindshelf.data.repository.NoteRepository
import com.example.mindshelf.data.sync.SyncCoordinator
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientToolDispatcher @Inject constructor(
    private val noteRepository: NoteRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val api: MindShelfApi,
    private val syncCoordinator: SyncCoordinator,
) {
    private val gson = Gson()
    suspend fun executeRead(name: String, arguments: Map<String, Any?>): Map<String, Any?> =
        when (name) {
            "search_knowledge_bases" -> knowledgeRepository.searchKnowledgeBases(
                arguments["query"]?.toString().orEmpty(),
            )
            "search_notes" -> noteRepository.searchNotes(
                query = arguments["query"]?.toString().orEmpty(),
                kbId = arguments["kb_id"]?.toString(),
                noteId = arguments["note_id"]?.toString(),
            )
            "web_search" -> executeWebSearch(arguments)
            else -> mapOf("error" to "未知工具: $name")
        }

    suspend fun buildWritePreview(name: String, arguments: Map<String, Any?>): ToolPreview {
        val action = arguments["action"]?.toString().orEmpty()
        return when (name) {
            "mutate_note" -> buildNotePreview(action, arguments)
            "mutate_knowledge_base" -> buildKbPreview(action, arguments)
            else -> ToolPreview(action = action)
        }
    }

    suspend fun executeWrite(name: String, arguments: Map<String, Any?>): Map<String, Any?> {
        val action = arguments["action"]?.toString().orEmpty()
        val result = when (name) {
            "mutate_note" -> mutateNote(action, arguments)
            "mutate_knowledge_base" -> mutateKb(action, arguments)
            else -> mapOf("error" to "未知写工具")
        }
        if (syncCoordinator.shouldWriteRemote()) {
            syncCoordinator.flushPending()
        }
        return result
    }

    private suspend fun executeWebSearch(arguments: Map<String, Any?>): Map<String, Any?> {
        val query = arguments["query"]?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return mapOf("error" to "缺少搜索关键词")
        return try {
            val data = api.webSearch(mapOf("query" to query)).data
            @Suppress("UNCHECKED_CAST")
            data as Map<String, Any?>
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "搜索失败"))
        }
    }

    private suspend fun buildNotePreview(action: String, args: Map<String, Any?>): ToolPreview {
        val noteId = args["note_id"]?.toString()?.trim().orEmpty()
        return when (action) {
            "create" -> ToolPreview(
                action = action,
                after = ToolContentSnapshot(
                    title = args["title"]?.toString().orEmpty(),
                    content = args["content"]?.toString()?.take(500).orEmpty(),
                ),
            )
            "update" -> {
                val note = noteId.takeIf { it.isNotBlank() }?.let { noteRepository.get(it) }
                ToolPreview(
                    action = action,
                    noteId = noteId.takeIf { it.isNotBlank() },
                    before = note?.let { ToolContentSnapshot(title = it.title, content = it.content) },
                    after = ToolContentSnapshot(
                        title = args["title"]?.toString() ?: note?.title,
                        content = args["content"]?.toString() ?: note?.content,
                    ),
                )
            }
            "delete" -> {
                val note = noteId.takeIf { it.isNotBlank() }?.let { noteRepository.get(it) }
                ToolPreview(
                    action = action,
                    noteId = noteId.takeIf { it.isNotBlank() },
                    before = note?.let {
                        ToolContentSnapshot(title = it.title, content = it.content.take(500))
                    },
                )
            }
            else -> ToolPreview(action = action, noteId = noteId.takeIf { it.isNotBlank() })
        }
    }

    private suspend fun buildKbPreview(action: String, args: Map<String, Any?>): ToolPreview {
        val kbId = args["kb_id"]?.toString()?.trim().orEmpty()
        val kbs = knowledgeRepository.listActiveKnowledgeBases()
        val kb = kbId.takeIf { it.isNotBlank() }?.let { id -> kbs.find { it.id == id } }
        return when (action) {
            "create" -> ToolPreview(
                action = action,
                after = ToolContentSnapshot(
                    name = args["name"]?.toString() ?: "新知识库",
                    description = args["description"]?.toString().orEmpty(),
                ),
            )
            "update" -> ToolPreview(
                action = action,
                kbId = kbId.takeIf { it.isNotBlank() },
                before = kb?.let { ToolContentSnapshot(name = it.name, description = it.description) },
                after = ToolContentSnapshot(
                    name = args["name"]?.toString() ?: kb?.name,
                    description = args["description"]?.toString() ?: kb?.description,
                ),
            )
            "delete" -> ToolPreview(
                action = action,
                kbId = kbId.takeIf { it.isNotBlank() },
                before = kb?.let { ToolContentSnapshot(name = it.name, description = it.description) },
            )
            else -> ToolPreview(action = action, kbId = kbId.takeIf { it.isNotBlank() })
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun mutateNote(action: String, args: Map<String, Any?>): Map<String, Any?> {
        return when (action) {
            "create" -> {
                val noteId = args["note_id"]?.toString()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                val kbIds = (args["knowledge_base_ids"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
                val note = noteRepository.create(
                    title = args["title"]?.toString().orEmpty(),
                    content = args["content"]?.toString().orEmpty(),
                    kbIds = kbIds,
                    id = noteId,
                )
                mapOf(
                    "note_id" to note.id,
                    "title" to note.title,
                    "content" to note.content,
                    "knowledge_base_ids" to note.knowledgeBaseIds,
                    "sync_version" to note.syncVersion,
                    "updated_at" to note.updatedAt,
                )
            }
            "update" -> {
                val noteId = args["note_id"]?.toString()?.trim().orEmpty()
                var note = noteId.takeIf { it.isNotBlank() }?.let { noteRepository.get(it) }
                if (note == null) {
                    val title = args["title"]?.toString()?.trim().orEmpty()
                    note = noteRepository.listActiveNotes().find { it.title == title }
                }
                if (note == null) return mapOf("error" to "笔记不存在")
                val kbIds = when (val raw = args["knowledge_base_ids"]) {
                    is List<*> -> raw.mapNotNull { it?.toString() }
                    else -> note.knowledgeBaseIds
                }
                val updated = noteRepository.update(
                    id = note.id,
                    title = args["title"]?.toString() ?: note.title,
                    content = args["content"]?.toString() ?: note.content,
                    syncVersion = note.syncVersion,
                    kbIds = kbIds,
                )
                mapOf(
                    "note_id" to updated.id,
                    "title" to updated.title,
                    "content" to updated.content,
                    "knowledge_base_ids" to updated.knowledgeBaseIds,
                    "sync_version" to updated.syncVersion,
                    "updated_at" to updated.updatedAt,
                )
            }
            "delete" -> {
                val noteId = args["note_id"]?.toString()?.trim().orEmpty()
                if (noteId.isBlank()) return mapOf("error" to "缺少 note_id")
                noteRepository.delete(noteId)
                mapOf("note_id" to noteId, "deleted" to true)
            }
            else -> mapOf("error" to "无效 action")
        }
    }

    private suspend fun mutateKb(action: String, args: Map<String, Any?>): Map<String, Any?> {
        return when (action) {
            "create" -> {
                val kb = knowledgeRepository.create(
                    name = args["name"]?.toString() ?: "新知识库",
                    id = args["kb_id"]?.toString(),
                    description = args["description"]?.toString().orEmpty(),
                )
                mapOf(
                    "kb_id" to kb.id,
                    "name" to kb.name,
                    "description" to kb.description,
                    "updated_at" to kb.updatedAt,
                )
            }
            "update" -> {
                val kbId = args["kb_id"]?.toString()?.trim().orEmpty()
                if (kbId.isBlank()) return mapOf("error" to "缺少 kb_id")
                val kb = knowledgeRepository.update(
                    kbId,
                    args["name"]?.toString() ?: "",
                    args["description"]?.toString() ?: "",
                )
                mapOf(
                    "kb_id" to kb.id,
                    "name" to kb.name,
                    "description" to kb.description,
                    "updated_at" to kb.updatedAt,
                )
            }
            "delete" -> {
                val kbId = args["kb_id"]?.toString()?.trim().orEmpty()
                if (kbId.isBlank()) return mapOf("error" to "缺少 kb_id")
                knowledgeRepository.delete(kbId)
                mapOf("kb_id" to kbId, "deleted" to true)
            }
            else -> mapOf("error" to "无效 action")
        }
    }

    fun toolResultJson(result: Map<String, Any?>): String = gson.toJson(result)

    fun parseArguments(json: String): Map<String, Any?> =
        runCatching {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(json, Map::class.java) as Map<String, Any?>
        }.getOrDefault(emptyMap())
}
