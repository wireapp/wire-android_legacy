package com.waz.zclient.storage.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class UserDatabaseMigration : Migration(124, 125) {
    override fun migrate(database: SupportSQLiteDatabase) {

        // "KeyValues" to "user_preference" Migration
        database.execSQL("CREATE TABLE IF NOT EXISTS `$USER_PREFERENCE_TABLE_NAME` (`key` TEXT PRIMARY KEY NOT NULL ,`value` TEXT)")
        database.execSQL("INSERT INTO $USER_PREFERENCE_TABLE_NAME SELECT * FROM $KEY_VALUES_TABLE_NAME")
        database.execSQL("DROP TABLE $KEY_VALUES_TABLE_NAME")

        // "Users" to "user" Migration
        database.execSQL("CREATE TABLE IF NOT EXISTS `$USER_TABLE_NAME` (`_id` TEXT PRIMARY KEY NOT NULL, `teamId` TEXT , `name` TEXT ," +
            " `email` TEXT , `phone` TEXT , `tracking_id` TEXT , `picture` TEXT , `accent` INTEGER , `skey` TEXT , `connection` TEXT , " +
            "`conn_timestamp` INTEGER , `conn_msg` TEXT , `conversation` TEXT , `relation` TEXT , `timestamp` INTEGER , `display_name` TEXT ," +
            " `verified` TEXT , `deleted` INTEGER NOT NULL , `availability` INTEGER , `handle` TEXT , `provider_id` TEXT , `integration_id` TEXT ," +
            " `expires_at` INTEGER , `managed_by` TEXT , `self_permissions` INTEGER , `copy_permissions` INTEGER , `created_by` TEXT )")
        database.execSQL("INSERT INTO $USER_TABLE_NAME SELECT * FROM $USERS_TABLE_NAME")
        database.execSQL("DROP TABLE $USERS_TABLE_NAME")
    }

    companion object {
        private const val USER_PREFERENCE_TABLE_NAME = "user_preference"
        private const val KEY_VALUES_TABLE_NAME = "KeyValues"

        private const val USER_TABLE_NAME = "user"
        private const val USERS_TABLE_NAME = "Users"

    }
}
