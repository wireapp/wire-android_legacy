@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val USER_DATABASE_MIGRATION_139_TO_140 = object : Migration(139, 140) {
    override fun migrate(database: SupportSQLiteDatabase) {
        with(database) {
            // Deleting encrypted events is safe: they can be re-fetched and decrypted again
            execSQL("DELETE FROM EncryptedPushNotificationEvents")
        }
    }
}
