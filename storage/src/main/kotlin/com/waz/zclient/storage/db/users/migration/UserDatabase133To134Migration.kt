@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.MigrationUtils

val USER_DATABASE_MIGRATION_133_TO_134 = object : Migration(133, 134) {
    override fun migrate(database: SupportSQLiteDatabase) {
        MigrationUtils.addColumn(
            database = database,
            tableName = "Conversations",
            columnName = "domain",
            columnType = MigrationUtils.ColumnType.TEXT
        )
    }
}
