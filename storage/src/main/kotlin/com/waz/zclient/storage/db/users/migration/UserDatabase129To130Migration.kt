@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.MigrationUtils
import com.waz.zclient.storage.db.MigrationUtils.addColumn

val USER_DATABASE_MIGRATION_129_TO_130 = object : Migration(129, 130) {
    override fun migrate(database: SupportSQLiteDatabase) {
        addColumn(
            database = database,
            tableName = "Messages",
            columnName = "client_id",
            columnType = MigrationUtils.ColumnType.TEXT
        )
        addColumn(
            database = database,
            tableName = "Messages",
            columnName = "error_code",
            columnType = MigrationUtils.ColumnType.INTEGER
        )
    }
}
