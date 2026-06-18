package com.example.mindshelf.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.mindshelf.data.local.dao.AiProviderDao
import com.example.mindshelf.data.local.dao.ChatDao
import com.example.mindshelf.data.local.dao.KnowledgeBaseDao
import com.example.mindshelf.data.local.dao.NoteDao
import com.example.mindshelf.data.local.dao.NoteKbDao
import com.example.mindshelf.data.local.entity.AiProviderEntity
import com.example.mindshelf.data.local.entity.BranchEntity
import com.example.mindshelf.data.local.entity.ConversationEntity
import com.example.mindshelf.data.local.entity.KnowledgeBaseEntity
import com.example.mindshelf.data.local.entity.MessageEntity
import com.example.mindshelf.data.local.entity.NoteEntity
import com.example.mindshelf.data.local.entity.NoteKbCrossRef
import com.example.mindshelf.data.local.entity.SyncStatus
import com.example.mindshelf.data.local.entity.ToolActionEntity

class SyncStatusConverter {
    @TypeConverter
    fun fromStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}

@Database(
    entities = [
        NoteEntity::class,
        KnowledgeBaseEntity::class,
        NoteKbCrossRef::class,
        ConversationEntity::class,
        BranchEntity::class,
        MessageEntity::class,
        AiProviderEntity::class,
        ToolActionEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
@TypeConverters(SyncStatusConverter::class)
abstract class MindShelfDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun knowledgeBaseDao(): KnowledgeBaseDao
    abstract fun noteKbDao(): NoteKbDao
    abstract fun chatDao(): ChatDao
    abstract fun aiProviderDao(): AiProviderDao
}
