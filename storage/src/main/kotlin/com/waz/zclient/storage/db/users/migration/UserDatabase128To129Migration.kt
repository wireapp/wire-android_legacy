@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.users.migration.MigrationUtils.deleteTable

val USER_DATABASE_MIGRATION_128_TO_129 = object : Migration(128, 129) {
    override fun migrate(database: SupportSQLiteDatabase) {
        deleteTable(
                database = database,
                tableName = "ContactHashes"
        )
        deleteTable(
                database = database,
                tableName = "ContactsOnWire"
        )
        deleteTable(
                database = database,
                tableName = "Contacts"
        )
        deleteTable(
                database = database,
                tableName = "EmailAddresses"
        )
        deleteTable(
                database = database,
                tableName = "PhoneNumbers"
        )
    }
}
