@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val USER_DATABASE_MIGRATION_139_TO_140 = object : Migration(139, 140) {
    override fun migrate(database: SupportSQLiteDatabase) {
        with(database) {
            // Deleting encrypted events is safe: they can be re-fetched and decrypted again
            execSQL("DELETE FROM EncryptedPushNotificationEvents")
            // Deleting decrypted events is tricky: they can not be decrypted again. Deleting a
            // message that was not processed yet causes the message to be lost forever.
            // Distinguishing which event was already processed and is still (wrongfully) in this
            // table will add a lot of complexity. It would be possible for some message types,
            // but it will be impossible for other. At the cost of losing some encrypted messages, the
            // easiest solution is to just delete the entire table.
            execSQL("DELETE FROM DecryptedPushNotificationEvents")
        }
    }
}
