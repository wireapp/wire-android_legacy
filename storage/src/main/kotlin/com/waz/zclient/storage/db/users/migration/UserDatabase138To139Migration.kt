@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.MigrationUtils
import java.io.Console

val USER_DATABASE_MIGRATION_138_TO_139 = object : Migration(138, 139) {
    override fun migrate(database: SupportSQLiteDatabase) {

        println("NOW MIGRATING FROM 138 TO 139")
        val previousTableName = "PushNotificationEvents"
        val createTableEncrypted = """
          CREATE TABLE IF NOT EXISTS EncryptedPushNotificationEvents(
             pushId TEXT NOT NULL,
             event_index INTEGER NOT NULL DEFAULT 0,
             event TEXT NOT NULL DEFAULT '',
             transient INTEGER NOT NULL DEFAULT 0,
             PRIMARY KEY (pushId, event_index))
          """.trimIndent()
        val createTableDecrypted = """
          CREATE TABLE IF NOT EXISTS DecryptedPushNotificationEvents(
             pushId TEXT NOT NULL,
             event_index INTEGER NOT NULL DEFAULT 0,
             event TEXT NOT NULL DEFAULT '',
             plain BLOB,
             transient INTEGER NOT NULL DEFAULT 0,
             PRIMARY KEY (pushId, event_index))
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
            execSQL(createTableEncrypted)
            execSQL(createTableDecrypted)
            if (com.waz.zclient.storage.db.MigrationUtils.tableExists(database, previousTableName)) {
                execSQL(copyFromPreviousTable)
                execSQL("DROP TABLE $previousTableName")
            }
        }
    }
}
