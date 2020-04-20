
package com.waz.zclient.storage.db.accountdata

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber")
val GLOBAL_DATABASE_MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
    }
}

@Suppress("MagicNumber")
val GLOBAL_DATABASE_MIGRATION_25_26 = object : Migration(25, 26) {

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
            name TEXT NOT NULL DEFAULT '', 
            creator TEXT NOT NULL DEFAULT '', 
            icon TEXT NOT NULL DEFAULT '')
            """.trimIndent()
        executeSimpleMigration(database, originalTableName, tempTableName, createTempTable)
    }

    private fun migrateCacheEntryTable(database: SupportSQLiteDatabase) {
        val tempTableName = "CacheEntryTemp"
        val originalTableName = "CacheEntry"
        val createTempTable = """
            CREATE TABLE IF NOT EXISTS $tempTableName (
            key TEXT PRIMARY KEY NOT NULL, 
            file TEXT NOT NULL DEFAULT '', 
            data BLOB, 
            lastUsed INTEGER NOT NULL DEFAULT 0, 
            timeout INTEGER NOT NULL DEFAULT 0, 
            enc_key TEXT, 
            path TEXT, 
            mime TEXT NOT NULL DEFAULT '', 
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
            _id TEXT PRIMARY KEY NOT NULL, 
            team_id TEXT, 
            cookie TEXT NOT NULL DEFAULT '', 
            access_token TEXT, 
            registered_push TEXT,
            sso_id TEXT DEFAULT NULL)
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
        val dropTempTableIfExists = "DROP TABLE IF EXISTS $tempTableName"
        val deleteNullValues = "DELETE FROM $originalTableName WHERE $primaryKey IS NULL"
        val copyAll = "INSERT OR IGNORE INTO $tempTableName SELECT * FROM $originalTableName"
        val dropOldTable = "DROP TABLE $originalTableName"
        val renameTableBack = "ALTER TABLE $tempTableName RENAME TO $originalTableName"
        with(database) {
            execSQL(dropTempTableIfExists)
            execSQL(createTempTable)
            execSQL(deleteNullValues)
            execSQL(copyAll)
            execSQL(dropOldTable)
            execSQL(renameTableBack)
        }
    }
}
