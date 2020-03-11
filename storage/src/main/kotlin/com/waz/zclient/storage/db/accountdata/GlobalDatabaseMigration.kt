package com.waz.zclient.storage.db.accountdata

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

const val START_VERSION = 24
const val END_VERSION = 25

val GLOBAL_DATABASE_MIGRATION_24_25 = object : Migration(START_VERSION, END_VERSION) {

    override fun migrate(database: SupportSQLiteDatabase) {
        migrateActiveAccounts(database)
        migrateCacheEntryTable(database)
        migrateTeamsTable(database)
    }

    private fun migrateTeamsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "TeamsTemp"
        val originalTableName = "Teams"
        val createTempTable = """
            CREATE TABLE IF NOT EXISTS $tempTableName (
            _id TEXT PRIMARY KEY NOT NULL, 
            name TEXT NOT NULL, 
            creator TEXT NOT NULL, 
            icon TEXT NOT NULL)
            """.trimIndent()
        executeSimpleMigration(database, originalTableName, tempTableName, createTempTable)
    }

    private fun migrateCacheEntryTable(database: SupportSQLiteDatabase) {
        val tempTableName = "CacheEntryTemp"
        val originalTableName = "CacheEntry"
        val createTempTable = """
            CREATE TABLE IF NOT EXISTS $tempTableName (
            key TEXT PRIMARY KEY NOT NULL, 
            file TEXT NOT NULL, 
            data BLOB, 
            lastUsed INTEGER NOT NULL, 
            timeout INTEGER NOT NULL, 
            enc_key TEXT, 
            path TEXT, 
            mime TEXT NOT NULL, 
            file_name TEXT,
            length INTEGER)
            """.trimIndent()
        executeSimpleMigration(database, originalTableName, tempTableName, createTempTable, "key")
    }

    private fun migrateActiveAccounts(database: SupportSQLiteDatabase) {
        val tempTableName = "ActiveAccountsTemp"
        val originalTableName = "ActiveAccounts"
        val createTempTable = """
            CREATE TABLE IF NOT EXISTS $tempTableName (
            `_id` TEXT PRIMARY KEY NOT NULL, 
            `team_id` TEXT, 
            `cookie` TEXT NOT NULL, 
            `access_token` TEXT, 
            `registered_push` TEXT,
            `sso_id` TEXT DEFAULT NULL)
            """.trimIndent()
        executeSimpleMigration(database, originalTableName, tempTableName, createTempTable)
    }

    private fun executeSimpleMigration(
        database: SupportSQLiteDatabase,
        originalTableName: String,
        tempTableName: String,
        createTempTable: String,
        primaryKey: String = "_id"
    ) {
        val deleteNullValues = "DELETE FROM $originalTableName WHERE $primaryKey IS NULL"
        val copyAll = "INSERT INTO $tempTableName SELECT * FROM $originalTableName"
        val dropOldTable = "DROP TABLE $originalTableName"
        val renameTableBack = "ALTER TABLE $tempTableName RENAME TO $originalTableName"
        with(database) {
            execSQL(createTempTable)
            execSQL(deleteNullValues)
            execSQL(copyAll)
            execSQL(dropOldTable)
            execSQL(renameTableBack)
        }
    }
}
