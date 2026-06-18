package com.example.mindshelf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.mindshelf.data.local.entity.CustomPageEntity
import com.example.mindshelf.data.local.entity.KnowledgeBaseEntity
import com.example.mindshelf.data.local.entity.NoteEntity
import com.example.mindshelf.data.local.entity.NoteKbCrossRef
import com.example.mindshelf.data.local.entity.SyncStatus
import com.example.mindshelf.data.local.entity.ToolActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("UPDATE notes SET deletedAt = :deletedAt, syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: Long, updatedAt: Long, status: SyncStatus)

    @Query(
        "SELECT * FROM notes WHERE deletedAt IS NULL AND syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE')",
    )
    suspend fun getPendingSync(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    suspend fun getAllActive(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getDeleted(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL AND syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeletes(): List<NoteEntity>

    @Query("UPDATE notes SET deletedAt = NULL, syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long, status: SyncStatus)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun purge(id: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

@Dao
interface KnowledgeBaseDao {
    @Query("SELECT * FROM knowledge_bases WHERE deletedAt IS NULL ORDER BY sortOrder, updatedAt DESC")
    fun observeActive(): Flow<List<KnowledgeBaseEntity>>

    @Query("SELECT * FROM knowledge_bases WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: String): KnowledgeBaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(kb: KnowledgeBaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<KnowledgeBaseEntity>)

    @Query("UPDATE knowledge_bases SET deletedAt = :deletedAt, syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: Long, updatedAt: Long, status: SyncStatus)

    @Query(
        "SELECT * FROM knowledge_bases WHERE deletedAt IS NULL AND syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE')",
    )
    suspend fun getPendingSync(): List<KnowledgeBaseEntity>

    @Query("SELECT * FROM knowledge_bases WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getDeleted(): List<KnowledgeBaseEntity>

    @Query("SELECT * FROM knowledge_bases WHERE deletedAt IS NULL ORDER BY sortOrder, updatedAt DESC")
    suspend fun getAllActive(): List<KnowledgeBaseEntity>

    @Query("SELECT * FROM knowledge_bases WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): KnowledgeBaseEntity?

    @Query("SELECT * FROM knowledge_bases WHERE deletedAt IS NOT NULL AND syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeletes(): List<KnowledgeBaseEntity>

    @Query("UPDATE knowledge_bases SET deletedAt = NULL, syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long, status: SyncStatus)

    @Query("DELETE FROM knowledge_bases WHERE id = :id")
    suspend fun purge(id: String)

    @Query("DELETE FROM knowledge_bases")
    suspend fun deleteAll()
}

@Dao
interface NoteKbDao {
    @Query("SELECT kbId FROM note_kb WHERE noteId = :noteId")
    suspend fun getKbIdsForNote(noteId: String): List<String>

    @Query("SELECT noteId FROM note_kb WHERE kbId = :kbId")
    suspend fun getNoteIdsForKb(kbId: String): List<String>

    @Query("DELETE FROM note_kb WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(refs: List<NoteKbCrossRef>)

    @Query("SELECT * FROM note_kb")
    fun observeAll(): Flow<List<NoteKbCrossRef>>

    @Query("DELETE FROM note_kb")
    suspend fun deleteAll()
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(items: List<com.example.mindshelf.data.local.entity.ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBranches(items: List<com.example.mindshelf.data.local.entity.BranchEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(items: List<com.example.mindshelf.data.local.entity.MessageEntity>)

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getConversations(): List<com.example.mindshelf.data.local.entity.ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversation(id: String): com.example.mindshelf.data.local.entity.ConversationEntity?

    @Query("SELECT * FROM conversations WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingConversations(): List<com.example.mindshelf.data.local.entity.ConversationEntity>

    @Query("SELECT * FROM branches WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingBranches(): List<com.example.mindshelf.data.local.entity.BranchEntity>

    @Query("SELECT * FROM messages WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingMessages(): List<com.example.mindshelf.data.local.entity.MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun countMessages(conversationId: String): Int

    @Query("SELECT * FROM branches WHERE conversationId = :conversationId ORDER BY createdAt")
    suspend fun getBranches(conversationId: String): List<com.example.mindshelf.data.local.entity.BranchEntity>

    @Query("SELECT * FROM branches WHERE id = :id LIMIT 1")
    suspend fun getBranch(id: String): com.example.mindshelf.data.local.entity.BranchEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND branchId = :branchId ORDER BY createdAt")
    suspend fun getMessages(conversationId: String, branchId: String): List<com.example.mindshelf.data.local.entity.MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt")
    suspend fun getAllMessages(conversationId: String): List<com.example.mindshelf.data.local.entity.MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND role = 'user' " +
            "ORDER BY createdAt LIMIT 1",
    )
    suspend fun getFirstUserMessage(conversationId: String): com.example.mindshelf.data.local.entity.MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): com.example.mindshelf.data.local.entity.MessageEntity?

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND branchId = :branchId")
    suspend fun deleteMessagesForBranch(conversationId: String, branchId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteAllMessages(conversationId: String)

    @Query("DELETE FROM branches WHERE conversationId = :conversationId")
    suspend fun deleteBranches(conversationId: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Transaction
    suspend fun replaceMessages(conversationId: String, branchId: String, messages: List<com.example.mindshelf.data.local.entity.MessageEntity>) {
        val existingSources = getMessages(conversationId, branchId).associate { it.id to it.searchSourcesJson }
        val merged = messages.map { msg ->
            if (msg.searchSourcesJson != "[]" && msg.searchSourcesJson.isNotBlank()) {
                msg
            } else {
                msg.copy(searchSourcesJson = existingSources[msg.id] ?: "[]")
            }
        }
        deleteMessagesForBranch(conversationId, branchId)
        upsertMessages(merged)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToolActions(items: List<ToolActionEntity>)

    @Query(
        "SELECT * FROM tool_actions WHERE conversationId = :conversationId AND branchId = :branchId " +
            "ORDER BY createdAt",
    )
    suspend fun getToolActions(conversationId: String, branchId: String): List<ToolActionEntity>

    @Query("DELETE FROM tool_actions WHERE conversationId = :conversationId")
    suspend fun deleteToolActions(conversationId: String)

    @Query("UPDATE messages SET searchSourcesJson = :json WHERE id = :messageId")
    suspend fun updateMessageSearchSources(messageId: String, json: String)

    @Query(
        "SELECT id, searchSourcesJson FROM messages WHERE conversationId = :conversationId " +
            "AND searchSourcesJson != '[]' AND searchSourcesJson != ''",
    )
    suspend fun getMessageSearchSourceRows(conversationId: String): List<MessageSearchSourceRow>
}

data class MessageSearchSourceRow(
    val id: String,
    val searchSourcesJson: String,
)

@Dao
interface PageDao {
    @Query("SELECT * FROM custom_pages WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<CustomPageEntity>>

    @Query("SELECT * FROM custom_pages WHERE deletedAt IS NULL AND pinned = 1 LIMIT 1")
    fun observePinned(): Flow<CustomPageEntity?>

    @Query("SELECT * FROM custom_pages WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: String): CustomPageEntity?

    @Query("SELECT * FROM custom_pages WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    suspend fun getAllActive(): List<CustomPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(page: CustomPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pages: List<CustomPageEntity>)

    @Query("UPDATE custom_pages SET deletedAt = :deletedAt, syncStatus = :status, updatedAt = :updatedAt, pinned = 0 WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: Long, updatedAt: Long, status: SyncStatus)

    @Query(
        "SELECT * FROM custom_pages WHERE deletedAt IS NULL AND syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE')",
    )
    suspend fun getPendingSync(): List<CustomPageEntity>

    @Query("SELECT * FROM custom_pages WHERE deletedAt IS NOT NULL AND syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeletes(): List<CustomPageEntity>

    @Query("SELECT * FROM custom_pages WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): CustomPageEntity?

    @Query("SELECT * FROM custom_pages WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getDeleted(): List<CustomPageEntity>

    @Query("UPDATE custom_pages SET deletedAt = NULL, syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long, status: SyncStatus)

    @Query("UPDATE custom_pages SET pinned = 0 WHERE pinned = 1 AND id != :exceptId AND deletedAt IS NULL")
    suspend fun clearOtherPins(exceptId: String)

    @Query("DELETE FROM custom_pages WHERE id = :id")
    suspend fun purge(id: String)

    @Query("DELETE FROM custom_pages")
    suspend fun deleteAll()
}

@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_providers ORDER BY createdAt")
    fun observeAll(): Flow<List<com.example.mindshelf.data.local.entity.AiProviderEntity>>

    @Query("SELECT * FROM ai_providers ORDER BY createdAt")
    suspend fun getAll(): List<com.example.mindshelf.data.local.entity.AiProviderEntity>

    @Query("SELECT * FROM ai_providers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): com.example.mindshelf.data.local.entity.AiProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: com.example.mindshelf.data.local.entity.AiProviderEntity)

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE ai_providers SET isDefault = 0")
    suspend fun clearDefault()
}
