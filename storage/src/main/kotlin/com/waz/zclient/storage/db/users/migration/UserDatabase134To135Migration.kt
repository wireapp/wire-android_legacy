@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.MigrationUtils

val USER_DATABASE_MIGRATION_134_TO_135 = object : Migration(134, 135) {
    override fun migrate(database: SupportSQLiteDatabase) {
        MigrationUtils.addColumn(
            database = database,
            tableName = "Users",
            columnName = "conversation_domain",
            columnType = MigrationUtils.ColumnType.TEXT
        )
    }
}
