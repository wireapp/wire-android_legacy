@file:Suppress("MagicNumber")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.MigrationUtils

val USER_DATABASE_MIGRATION_137_TO_138 = object : Migration(137, 138) {
    override fun migrate(database: SupportSQLiteDatabase) {
        MigrationUtils.deleteTable(database,"FCMNotifications")
        MigrationUtils.deleteTable(database,"NotificationData")
        MigrationUtils.deleteTable(database,"FCMNotificationStats")
    }
}
