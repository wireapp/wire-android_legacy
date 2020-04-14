package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.users.migration.MigrationUtils.executeSimpleMigration

val USER_DATABASE_MIGRATION_127_TO_128 = object : Migration(127, 128) {
    override fun migrate(database: SupportSQLiteDatabase) {
        migrateConversationsTable(database)
        migrateContactHashesTable(database)
    }

    private fun migrateConversationsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ConversationsTemp"
        val originalTableName = "Conversations"
        val searchKey = "search_key"
        val createTempTable = """
                CREATE TABLE $tempTableName (
                _id TEXT PRIMARY KEY NOT NULL,
                remote_id TEXT NOT NULL,
                name TEXT,
                creator TEXT NOT NULL,
                conv_type INTEGER NOT NULL,
                team TEXT,
                is_managed INTEGER,
                last_event_time INTEGER NOT NULL,
                is_active INTEGER NOT NULL,
                last_read INTEGER NOT NULL,
                muted_status INTEGER NOT NULL,
                mute_time INTEGER NOT NULL,
                archived INTEGER NOT NULL,
                archive_time INTEGER NOT NULL,
                cleared INTEGER,
                generated_name TEXT NOT NULL,
                $searchKey TEXT, 
                unread_count INTEGER NOT NULL, 
                unsent_count INTEGER NOT NULL, 
                hidden INTEGER NOT NULL, 
                missed_call TEXT,
                incoming_knock TEXT, 
                verified TEXT NOT NULL, 
                ephemeral INTEGER,
                global_ephemeral INTEGER,
                unread_call_count INTEGER NOT NULL,
                unread_ping_count INTEGER NOT NULL,
                access TEXT, 
                access_role TEXT, 
                link TEXT, 
                unread_mentions_count INTEGER NOT NULL, 
                unread_quote_count INTEGER NOT NULL, 
                receipt_mode INTEGER 
                )""".trimIndent()
        val conversationSearchKeyIndex = """
            CREATE INDEX IF NOT EXISTS Conversation_search_key on $originalTableName ($searchKey)
            """.trimIndent()
        executeSimpleMigration(
                database,
                originalTableName,
                tempTableName,
                createTempTable,
                conversationSearchKeyIndex
        )
    }

    private fun migrateContactHashesTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ContactHashesTemp"
        val originalTableName = "ContactHashes"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             _id TEXT PRIMARY KEY NOT NULL, 
             hashes TEXT
             )""".trimIndent()

        executeSimpleMigration(
                database = database,
                originalTableName = originalTableName,
                tempTableName = tempTableName,
                createTempTable = createTempTable
        )
    }
}