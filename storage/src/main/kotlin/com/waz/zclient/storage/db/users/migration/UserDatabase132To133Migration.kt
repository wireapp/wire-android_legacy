@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.users.migration.MigrationUtils.addColumn

private const val TABLE = "Conversations"
private const val COLUMN = "legal_hold_status"
private const val PENDING_APPROVAL = "1"
private const val ENABLED = "2"

val USER_DATABASE_MIGRATION_132_TO_133 = object : Migration(132, 133) {
    override fun migrate(database: SupportSQLiteDatabase) {
        with(database) {
            // The "pending approval" legal hold status is deprecated and should
            // be treated as "enabled".
            val update = """
                UPDATE $TABLE
                SET $COLUMN = $ENABLED
                WHERE $COLUMN = $PENDING_APPROVAL
            """.trimIndent()

            execSQL(update)
        }
    }
}
