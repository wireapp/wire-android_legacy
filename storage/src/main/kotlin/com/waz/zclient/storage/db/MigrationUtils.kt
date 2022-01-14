package com.waz.zclient.storage.db

import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

object MigrationUtils {

    enum class ColumnType(val defaultValue: String) {
        INTEGER("0"), TEXT("''")
    }

    fun tableExists(database: SupportSQLiteDatabase, tableName: String): Boolean {
        val query = "SELECT DISTINCT tbl_name FROM sqlite_master WHERE tbl_name = '$tableName'"
        database.query(query).use { cursor ->
            return cursor != null && cursor.count > 0
        }
    }

    fun recreateAndTryMigrate(
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
            } else {
                Log.w("MigrationUtils", "The original database table is missing: $originalTableName")
            }
            execSQL(renameTableBack)
            indicesCalls.forEach {
                execSQL(it)
            }
        }
    }

    fun deleteTable(database: SupportSQLiteDatabase, tableName: String) {
        with(database) {
            execSQL("DROP TABLE IF EXISTS $tableName")
        }
    }

    fun addColumn(
        database: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
        columnType: ColumnType,
        canBeNull: Boolean = true
    ) {
        val execStr = "ALTER TABLE $tableName ADD COLUMN $columnName ${columnType.name}" +
            if (canBeNull) {
                ""
            } else {
                " NOT NULL DEFAULT ${columnType.defaultValue}"
            }

        with(database) {
            execSQL(execStr)
        }
    }
}
