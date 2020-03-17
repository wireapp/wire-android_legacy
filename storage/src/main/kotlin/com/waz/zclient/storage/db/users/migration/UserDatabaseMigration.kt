@file:Suppress("MagicNumber")

package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.BuildConfig

//Shared keys
private const val CLIENT_ID_KEY = "id"
private const val CLIENT_LABEL_KEY = "label"
private const val CLIENT_LOCATION_LAT_KEY = "lat"
private const val CLIENT_LOCATION_LONG_KEY = "lon"
private const val CLIENT_ENC_KEY = "encKey"
private const val CLIENT_MAC_KEY = "macKey"
private const val CLIENT_VERIFICATION_KEY = "verification"
private const val CLIENT_MODEL_KEY = "model"
private const val CLIENT_CLASS_KEY = "class"

//New table keys
private const val NEW_CLIENT_TABLE_NAME = "client"
private const val NEW_CLIENT_LOCATION_NAME_KEY = "locationName"
private const val NEW_CLIENT_TIME_KEY = "time"
private const val NEW_CLIENT_TYPE_KEY = "type"

val USER_DATABASE_MIGRATION_126_TO_127 = object : Migration(126, 127) {
    override fun migrate(database: SupportSQLiteDatabase) {
        if (BuildConfig.KOTLIN_CORE) {
            migrateClientTable(database)
        }

        migrateUserTable(database)
        migrateKeyValuesTable(database)
        migrateUploadAssetTable(database)
        migrateDownloadAssetTable(database)
        migrateAssets2Table(database)
        migrateFcmNotificationsTable(database)
        migrateFcmNotificationStatsTable(database)
        migrateFoldersTable(database)
        migrateConversationFoldersTable(database)
        migrateConversationRoleActionTable(database)

        //TODO Move this to 127 - 128 Migration when finished with migration bug
        createButtonsTable(database)
    }

    private fun migrateUploadAssetTable(database: SupportSQLiteDatabase) {
        val tempTableName = "UploadAssetsTemp"
        val originalTableName = "UploadAssets"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             _id TEXT NOT NULL, 
             source TEXT NOT NULL, 
             name TEXT NOT NUL, 
             sha BLOB NOT NUL, 
             md5 BLOB NOT NUL, 
             mime TEXT NOT NUL, 
             preview TEXT NOT NUL, 
             uploaded INTEGER NOT NUL, 
             size INTEGER NOT NUL, 
             retention INTEGER NOT NUL, 
             public INTEGER NOT NUL, 
             encryption TEXT NOT NUL, 
             encryption_salt TEXT, 
             details TEXT NOT NUL, 
             status INTEGER NOT NUL, 
             asset_id TEXT, 
             PRIMARY KEY (_id)
             )""".trimIndent()
        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateDownloadAssetTable(database: SupportSQLiteDatabase) {
        val tempTableName = "DownloadAssetsTemp"
        val originalTableName = "DownloadAssets"
        val createTempTable = """
              CREATE TABLE $tempTableName (
              _id TEXT NOT NULL, 
              mime TEXT NOT NULL, 
              name TEXT NOT NULL , 
              preview TEXT NOT NULL, 
              details TEXT NOT NULL, 
              downloaded INTEGER NOT NULL, 
              size INTEGER NOT NULL, 
              status INTEGER NOT NULL, 
              PRIMARY KEY (_id)
              )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateAssets2Table(database: SupportSQLiteDatabase) {
        val tempTableName = "Assets2Temp"
        val originalTableName = "Assets2"
        val createTempTable = """
              CREATE TABLE $tempTableName (
              _id TEXT NOT NULL, 
              token TEXT, 
              name TEXT NOT NULL, 
              encryption TEXT NOT NULL, 
              mime TEXT NOT NULL, 
              sha BLOB NOT NULL, 
              size INTEGER NOT NULL, 
              source TEXT, 
              preview TEXT, 
              details TEXT NOT NULL, 
              conversation_id TEXT, 
              PRIMARY KEY (_id)
              )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateFcmNotificationsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "FCMNotificationsTemp"
        val originalTableName = "FCMNotifications"
        val createTempTable = """
              CREATE TABLE $tempTableName (
              _id TEXT NOT NULL, 
              stage TEXT NOT NULL, 
              stage_start_time INTEGER NOT NULL, 
              PRIMARY KEY (_id, stage)
              )""".trimIndent()
        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateKeyValuesTable(database: SupportSQLiteDatabase) {
        val keyValuesTempTable = "KeyValuesTemp"
        val keyValuesOriginalTable = "KeyValues"
        val createTempKeyValues = """
                CREATE TABLE IF NOT EXISTS `$keyValuesTempTable` (
                'key' TEXT PRIMARY KEY NOT NULL ,
                `value` TEXT)
                """.trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = keyValuesOriginalTable,
            tempTableName = keyValuesTempTable,
            createTempTable = createTempKeyValues
        )
    }

    private fun migrateUserTable(database: SupportSQLiteDatabase) {
        val usersTempTable = "UsersTemp"
        val usersOriginalTable = "Users"
        val createTempUserTable = """
              | CREATE TABLE $usersTempTable (
              | _id TEXT PRIMARY KEY NOT NULL,
              | teamId TEXT, name TEXT, email TEXT, phone TEXT, tracking_id TEXT,
              | picture TEXT, accent INTEGER, skey TEXT, connection TEXT, conn_timestamp INTEGER,
              | conn_msg TEXT, conversation TEXT, relation TEXT, timestamp INTEGER,
              | verified TEXT, deleted INTEGER, availability INTEGER,
              | handle TEXT, provider_id TEXT, integration_id TEXT, expires_at INTEGER,
              | managed_by TEXT, self_permissions INTEGER, copy_permissions INTEGER, created_by TEXT
              | )""".trimIndent()
        executeSimpleMigration(
            database = database,
            originalTableName = usersOriginalTable,
            tempTableName = usersTempTable,
            createTempTable = createTempUserTable
        )
    }

    private fun migrateFcmNotificationStatsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "FCMNotificationStatsTemp"
        val originalTableName = "FCMNotificationStats"
        val createTempTable = """
              CREATE TABLE $tempTableName (
              stage TEXT PRIMARY KEY NOT NULL, 
              bucket1 INTEGER NOT NULL, 
              bucket2 INTEGER NOT NULL, 
              bucket3 INTEGER NOT NULL 
              )""".trimIndent()
        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateFoldersTable(database: SupportSQLiteDatabase) {
        val tempTableName = "FoldersTemp"
        val originalTableName = "Folders"
        val createTempTable = """
              CREATE TABLE $tempTableName (
              _id TEXT PRIMARY KEY NOT NULL, 
              name TEXT NOT NULL, 
              type INTEGER NOT NULL
              )""".trimIndent()
        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateConversationFoldersTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ConversationFoldersTemp"
        val originalTableName = "ConversationFolders"
        val createTempTable = """
              CREATE TABLE $tempTableName (
              conv_id TEXT NOT NULL, 
              folder_id TEXT NOT NULL, 
              PRIMARY KEY (conv_id, folder_id)
              )""".trimIndent()
        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateConversationRoleActionTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ConversationRoleActionTemp"
        val originalTableName = "ConversationRoleAction"
        val createTempTable = """
               CREATE TABLE $tempTableName (
               label TEXT NOT NULL, 
               action TEXT NOT NULL, 
               conv_id TEXT NOT NULL, 
               PRIMARY KEY (label, action, conv_id)
               )""".trimIndent()
        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun executeSimpleMigration(
        database: SupportSQLiteDatabase,
        originalTableName: String,
        tempTableName: String,
        createTempTable: String
    ) {
        val copyAll = "INSERT INTO $tempTableName SELECT * FROM $originalTableName"
        val dropOldTable = "DROP TABLE $originalTableName"
        val renameTableBack = "ALTER TABLE $tempTableName RENAME TO $originalTableName"
        with(database) {
            execSQL(createTempTable)
            execSQL(copyAll)
            execSQL(dropOldTable)
            execSQL(renameTableBack)
        }
    }

    private fun createButtonsTable(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `Buttons` (
                `message_id` TEXT NOT NULL, 
                `button_id` TEXT NOT NULL, 
                `title` TEXT NOT NULL,
                `ordinal` INTEGER NOT NULL,
                `state` INTEGER NOT NULL, 
                `error` TEXT, 
                PRIMARY KEY(`message_id`, `button_id`)
            )""".trimIndent()
        )
    }

    //TODO still needs determining what to do with this one.
    private fun migrateClientTable(database: SupportSQLiteDatabase) {
        database.execSQL("""CREATE TABLE '$NEW_CLIENT_TABLE_NAME' (
                |'$CLIENT_ID_KEY' TEXT NOT NULL, 
                |'$NEW_CLIENT_TIME_KEY' TEXT NOT NULL,  
                |'$CLIENT_LABEL_KEY' TEXT NOT NULL, 
                |'$NEW_CLIENT_TYPE_KEY' TEXT NOT NULL, 
                |'$CLIENT_CLASS_KEY' TEXT NOT NULL ,
                |'$CLIENT_MODEL_KEY' TEXT NOT NULL,  
                |'$CLIENT_LOCATION_LAT_KEY' REAL NOT NULL,  
                |'$CLIENT_LOCATION_LONG_KEY' REAL NOT NULL, 
                |'$NEW_CLIENT_LOCATION_NAME_KEY' TEXT,  
                |'$CLIENT_VERIFICATION_KEY' TEXT NOT NULL,  
                |'$CLIENT_ENC_KEY' TEXT NOT NULL,  
                |'$CLIENT_MAC_KEY' TEXT NOT NULL,  
                |PRIMARY KEY('$CLIENT_ID_KEY')
                |)""".trimMargin())
    }

}
