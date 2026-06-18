package com.example.mindshelf.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mindshelf.data.local.MindShelfDatabase
import com.example.mindshelf.data.local.dao.AiProviderDao
import com.example.mindshelf.data.local.dao.ChatDao
import com.example.mindshelf.data.local.dao.KnowledgeBaseDao
import com.example.mindshelf.data.local.dao.NoteDao
import com.example.mindshelf.data.local.dao.NoteKbDao
import com.example.mindshelf.data.local.dao.PageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN reasoning TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ai_providers (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    baseUrl TEXT NOT NULL,
                    model TEXT NOT NULL,
                    isDefault INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN segmentsJson TEXT NOT NULL DEFAULT '[]'")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tool_actions (
                    id TEXT NOT NULL PRIMARY KEY,
                    conversationId TEXT NOT NULL,
                    branchId TEXT NOT NULL,
                    anchorMessageId TEXT,
                    tool TEXT NOT NULL,
                    previewJson TEXT NOT NULL,
                    status TEXT NOT NULL,
                    resultMessage TEXT,
                    errorMessage TEXT,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE messages ADD COLUMN searchSourcesJson TEXT NOT NULL DEFAULT '[]'",
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tool_actions ADD COLUMN segmentIndex INTEGER")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE conversations ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'",
            )
            db.execSQL(
                "ALTER TABLE branches ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'",
            )
            db.execSQL(
                "ALTER TABLE messages ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'",
            )
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS custom_pages (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    schemaJson TEXT NOT NULL,
                    dataBindings TEXT NOT NULL,
                    pinned INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    deletedAt INTEGER,
                    syncStatus TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MindShelfDatabase =
        Room.databaseBuilder(context, MindShelfDatabase::class.java, "mindshelf.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
            )
            .build()

    @Provides
    fun provideNoteDao(db: MindShelfDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideKnowledgeBaseDao(db: MindShelfDatabase): KnowledgeBaseDao = db.knowledgeBaseDao()

    @Provides
    fun provideNoteKbDao(db: MindShelfDatabase): NoteKbDao = db.noteKbDao()

    @Provides
    fun provideChatDao(db: MindShelfDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideAiProviderDao(db: MindShelfDatabase): AiProviderDao = db.aiProviderDao()

    @Provides
    fun providePageDao(db: MindShelfDatabase): PageDao = db.pageDao()
}
