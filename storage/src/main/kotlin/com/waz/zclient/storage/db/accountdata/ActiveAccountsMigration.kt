package com.waz.zclient.storage.db.accountdata

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.BuildConfig

private const val START_VERSION = 24
private const val END_VERSION = 25

val ACTIVE_ACCOUNTS_MIGRATION = object : Migration(START_VERSION, END_VERSION) {
    override fun migrate(database: SupportSQLiteDatabase) {
        if (BuildConfig.KOTLIN_CORE) {
            val tempTableName = "ActiveAccountsTemp"
            val createTempTable = """
            CREATE TABLE IF NOT EXISTS $tempTableName (
            `_id` TEXT PRIMARY KEY NOT NULL, 
            `team_id` TEXT, 
            `cookie` TEXT NOT NULL, 
            `access_token` TEXT, 
            `registered_push` TEXT,
            `sso_id` TEXT)
            """.trimIndent()
            val deleteNullValues = "DELETE FROM ActiveAccounts WHERE _id IS NULL"
            val copyAll = "INSERT INTO $tempTableName SELECT * FROM ActiveAccounts"
            val dropOldTable = "DROP TABLE ActiveAccounts"
            val renameTableBack = "ALTER TABLE $tempTableName RENAME TO ActiveAccounts"
            with(database) {
                execSQL(createTempTable)
                execSQL(deleteNullValues)
                execSQL(copyAll)
                execSQL(dropOldTable)
                execSQL(renameTableBack)
            }
        }
    }
}
