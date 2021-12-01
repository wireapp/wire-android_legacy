@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.MigrationUtils

val USER_DATABASE_MIGRATION_135_TO_136 = object : Migration(135, 136) {
    override fun migrate(database: SupportSQLiteDatabase) {
        MigrationUtils.deleteTable(database,"FCMNotifications")
        MigrationUtils.deleteTable(database,"NotificationData")
        MigrationUtils.deleteTable(database,"FCMNotificationStats")
    }
}
