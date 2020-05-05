package com.waz.zclient.storage.db.users.migration

import androidx.sqlite.db.SupportSQLiteDatabase

object MigrationUtils {
    private fun tableExists(database: SupportSQLiteDatabase, tableName: String): Boolean {
        val query = "SELECT DISTINCT tbl_name FROM sqlite_master WHERE tbl_name = '$tableName'"
        database.query(query).use { cursor ->
            if (cursor != null) {
                if (cursor.count > 0) {
                    return true
                }
            }
            return false
        }
    }

    fun executeSimpleMigration(
        database: SupportSQLiteDatabase,
        originalTableName: String,
        tempTableName: String,
        createTempTable: String,
        vararg indicesCalls: String
    ) {
        val dropTempTableIfExists = "DROP TABLE IF EXISTS $tempTableName"
        val copyAll = "INSERT OR IGNORE INTO $tempTableName SELECT * FROM $originalTableName"
        val dropOldTable = "DROP TABLE $originalTableName"
        val renameTableBack = "ALTER TABLE $tempTableName RENAME TO $originalTableName"
        with(database) {
            execSQL(dropTempTableIfExists)
            execSQL(createTempTable)
            if (tableExists(database, originalTableName)) {
                execSQL(copyAll)
                execSQL(dropOldTable)
            }
            execSQL(renameTableBack)
            indicesCalls.forEach {
                execSQL(it)
            }
        }
    }
}
