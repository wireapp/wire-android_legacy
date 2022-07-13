@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.Console

val USER_DATABASE_MIGRATION_138_TO_139 = object : Migration(138, 139) {
    override fun migrate(database: SupportSQLiteDatabase) {

        println("NOW MIGRATING FROM 138 TO 139")
        val previousTableName = "PushNotificationEvents"
        val createTable = """
          CREATE TABLE IF NOT EXISTS EncryptedPushNotificationEvents(
             pushId TEXT,
             event_index INTEGER,
             event TEXT,
             PRIMARY KEY (pushId, event_index))"
          """.trimIndent()
        val copyFromPreviousTable = """
            INSERT INTO DecryptedPushNotificationEvents(
            pushId,
            event_index,
            event,
            plain
            )
            SELECT
            pushId,
            event_index,
            event,
            plain
            FROM $previousTableName
            WHERE decrypted = 1
        """.trimIndent()

        with(database) {
            kotlin.io.println("NOW MIGRATING: create table")
            //execSQL(createTable)
            kotlin.io.println("NOW MIGRATING: check table exists")
            if (com.waz.zclient.storage.db.MigrationUtils.tableExists(database, previousTableName)) {
                kotlin.io.println("NOW MIGRATING: copy previous table")
                //execSQL(copyFromPreviousTable)
                kotlin.io.println("NOW MIGRATING: deleting table")
                execSQL("DROP TABLE $previousTableName")
            }
            kotlin.io.println("NOW MIGRATING: done!")
        }
    }
}
