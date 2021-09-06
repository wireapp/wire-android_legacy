@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.MigrationUtils
import com.waz.zclient.storage.db.MigrationUtils.addColumn

private const val USER_TABLE_NAME = "Users"
private const val DOMAIN_COLUMN_NAME = "domain"

val USER_DATABASE_MIGRATION_130_TO_131 = object : Migration(130, 131) {
    override fun migrate(database: SupportSQLiteDatabase) {
        addColumn(
            database = database,
            tableName = USER_TABLE_NAME,
            columnName = DOMAIN_COLUMN_NAME,
            columnType = MigrationUtils.ColumnType.TEXT
        )
    }
}
