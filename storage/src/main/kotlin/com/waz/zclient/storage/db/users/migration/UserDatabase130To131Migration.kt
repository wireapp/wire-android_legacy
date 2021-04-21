@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.users.migration.MigrationUtils.addColumn

val USER_DATABASE_MIGRATION_130_TO_131 = object : Migration(130, 131) {
    override fun migrate(database: SupportSQLiteDatabase) {
        addColumn(
            database = database,
            tableName = "Users",
            columnName = "domain",
            columnType = MigrationUtils.ColumnType.TEXT
        )
    }
}

