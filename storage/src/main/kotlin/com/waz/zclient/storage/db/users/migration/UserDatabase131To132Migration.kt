@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.MigrationUtils
import com.waz.zclient.storage.db.MigrationUtils.addColumn

val USER_DATABASE_MIGRATION_131_TO_132 = object : Migration(131, 132) {
    override fun migrate(database: SupportSQLiteDatabase) {
        addColumn(
            database = database,
            tableName = "Conversations",
            columnName = "legal_hold_status",
            columnType = MigrationUtils.ColumnType.INTEGER,
            canBeNull = false
        )
    }
}
