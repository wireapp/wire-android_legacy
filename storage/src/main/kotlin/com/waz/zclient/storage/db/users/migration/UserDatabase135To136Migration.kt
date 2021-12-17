@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.MigrationUtils

val USER_DATABASE_MIGRATION_135_TO_136 = object : Migration(135, 136) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val tempTableName = "Assets2Temp"
        val originalTableName = "Assets2"
        val createTempTable = """
              CREATE TABLE $tempTableName (
              _id TEXT PRIMARY KEY NOT NULL,
              token TEXT,
              name TEXT NOT NULL DEFAULT '',
              encryption TEXT NOT NULL DEFAULT '',
              mime TEXT NOT NULL DEFAULT '',
              sha BLOB,
              size INTEGER NOT NULL DEFAULT 0,
              source TEXT,
              preview TEXT,
              details TEXT NOT NULL DEFAULT ''
              )""".trimIndent()

        MigrationUtils.recreateAndTryMigrate(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }
}
