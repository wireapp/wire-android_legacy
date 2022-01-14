@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.MigrationUtils

val USER_DATABASE_MIGRATION_135_TO_136 = object : Migration(135, 136) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val tempTableName = "Assets2Temp"
        val originalTableName = "Assets2"
        val dropTempTableIfExists = "DROP TABLE IF EXISTS $tempTableName"
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
        val copyAll = """
            INSERT OR IGNORE INTO $tempTableName
            SELECT _id, token, name, encryption, mime, sha, size, source, preview,details
            FROM $originalTableName
        """.trimIndent()
        val dropOldTable = "DROP TABLE $originalTableName"
        val renameTableBack = "ALTER TABLE $tempTableName RENAME TO $originalTableName"

        with(database) {
            execSQL(dropTempTableIfExists)
            execSQL(createTempTable)
            if (MigrationUtils.tableExists(database, originalTableName)) {
                execSQL(copyAll)
                execSQL(dropOldTable)
            } else {
                Log.w("MigrationUtils", "The original database table is missing: $originalTableName")
            }
            execSQL(renameTableBack)
        }
    }
}
