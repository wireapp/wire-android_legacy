package com.waz.zclient.storage.db.users.migration

import androidx.sqlite.db.SupportSQLiteDatabase

object MigrationUtils {
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
            execSQL(copyAll)
            execSQL(dropOldTable)
            execSQL(renameTableBack)
            indicesCalls.forEach {
                execSQL(it)
            }
        }
    }
}
