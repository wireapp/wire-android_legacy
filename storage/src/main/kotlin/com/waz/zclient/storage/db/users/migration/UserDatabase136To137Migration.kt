@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.MigrationUtils

val USER_DATABASE_MIGRATION_136_TO_137 = object : Migration(136, 137) {
    override fun migrate(database: SupportSQLiteDatabase) {
        MigrationUtils.addColumn(
            database = database,
            tableName = "Assets2",
            columnName = "domain",
            columnType = MigrationUtils.ColumnType.TEXT
        )
    }
}

