@file:Suppress("MagicNumber", "TooManyFunctions")

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
        migrateAssetsTable(database)
        migrateConversationsTable(database)
        migrateKeyValuesTable(database)
        migrateNotificationData(database)
        migrateContactHashesTable(database)
        migrateContactsOnWire(database)
        migrateClientsTable(database)
        migrateLikingTable(database)
        migrateContactsTable(database)
        migrateEmailAddressTable(database)
        migratePhoneNumbersTable(database)
        migrateMessageDeletionTable(database)
        migrateEditHistoryTable(database)
        migrateMessageContentIndexTable(database)
        migratePushNotificationEvents(database)
        migrateReadRecieptsTable(database)
        migratePropertiesTable(database)
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

    private fun migrateUserTable(database: SupportSQLiteDatabase) {
        val tempTableName = "UsersTemp"
        val originalTableName = "Users"
        val createTempTable = """
        CREATE TABLE $tempTableName (
            _id TEXT PRIMARY KEY NOT NULL,
            teamId TEXT,
            name TEXT NOT NULL,
            email TEXT, 
            phone TEXT, 
            tracking_id TEXT, 
            picture TEXT, 
            accent INTEGER NOT NULL, 
            skey TEXT NOT NULL, 
            connection TEXT NOT NULL, 
            conn_timestamp INTEGER NOT NULL, 
            conn_msg TEXT, 
            conversation TEXT, 
            relation TEXT NOT NULL, 
            timestamp INTEGER, 
            verified TEXT NOT NULL, 
            deleted INTEGER NOT NULL, 
            availability INTEGER NOT NULL, 
            handle TEXT, 
            provider_id TEXT, 
            integration_id TEXT, 
            expires_at INTEGER, 
            managed_by TEXT, 
            self_permissions INTEGER NOT NULL, 
            copy_permissions INTEGER NOT NULL, 
            created_by TEXT 
        )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateAssetsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "AssetsTemp"
        val originalTableName = "Assets"
        val createTempTable = """
                CREATE TABLE $tempTableName (
                _id TEXT PRIMARY KEY NOT NULL,
                asset_type TEXT NOT NULL,
                data TEXT NOT NULL
                )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateConversationsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ConversationsTemp"
        val originalTableName = "Conversations"
        val createTempTable = """
                CREATE TABLE $tempTableName (
                _id TEXT PRIMARY KEY NOT NULL,
                remote_id TEXT NOT NULL,
                name TEXT ,
                creator TEXT NOT NULL,
                conv_type INTEGER NOT NULL,
                team TEXT,
                is_managed INTEGER,
                last_event_time INTEGER NOT NULL,
                is_active INTEGER NOT NULL,
                last_read INTEGER NOT NULL,
                muted_status INTEGER NOT NULL,
                mute_time INTEGER NOT NULL,
                archived INTEGER NOT NULL,
                archive_time INTEGER NOT NULL,
                cleared INTEGER,
                generated_name TEXT NOT NULL,
                search_key TEXT, 
                unread_count INTEGER NOT NULL, 
                unsent_count INTEGER NOT NULL, 
                hidden INTEGER NOT NULL, 
                missed_call TEXT,
                incoming_knock TEXT, 
                verified TEXT NOT NULL, 
                ephemeral INTEGER,
                global_ephemeral INTEGER,
                unread_call_count INTEGER NOT NULL,
                unread_ping_count INTEGER NOT NULL,
                access TEXT NOT NULL, 
                access_role TEXT, 
                link TEXT, 
                unread_mentions_count INTEGER NOT NULL, 
                unread_quote_count INTEGER NOT NULL, 
                receipt_mode INTEGER 
                )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateKeyValuesTable(database: SupportSQLiteDatabase) {
        val tempTableName = "KeyValuesTemp"
        val originalTableName = "KeyValues"
        val createTempTable = """
                CREATE TABLE $tempTableName (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
                )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateNotificationData(database: SupportSQLiteDatabase) {
        val tempTableName = "NotificationDataTemp"
        val originalTableName = "NotificationData"
        val createTempTable = """
            CREATE TABLE $tempTableName (
            _id TEXT PRIMARY KEY NOT NULL, 
            data TEXT NOT NULL 
            )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateContactHashesTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ContactHashesTemp"
        val originalTableName = "ContactHashes"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             _id TEXT PRIMARY KEY NOT NULL, 
             hashes TEXT NOT NULL
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateContactsOnWire(database: SupportSQLiteDatabase) {
        val tempTableName = "ContactsOnWireTemp"
        val originalTableName = "ContactsOnWire"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             user TEXT NOT NULL, 
             contact TEXT NOT NULL, 
             PRIMARY KEY (user, contact)
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateClientsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ClientsTemp"
        val originalTableName = "Clients"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             _id TEXT PRIMARY KEY NOT NULL, 
             data TEXT NOT NULL 
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateLikingTable(database: SupportSQLiteDatabase) {
        val tempTableName = "LikingsTemp"
        val originalTableName = "Likings"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             message_id TEXT NOT NULL, 
             user_id TEXT NOT NULL, 
             timestamp INTEGER NOT NULL, 
             action INTEGER NOT NULL, 
             PRIMARY KEY (message_id, user_id)
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateContactsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ContactsTemp"
        val originalTableName = "Contacts"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             _id TEXT PRIMARY KEY NOT NULL, 
             name TEXT NOT NULL, 
             name_source INTEGER NOT NULL, 
             sort_key TEXT NOT NULL, 
             search_key TEXT NOT NULL
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateEmailAddressTable(database: SupportSQLiteDatabase) {
        val tempTableName = "EmailAddressesTemp"
        val originalTableName = "EmailAddresses"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             contact TEXT NOT NULL, 
             email_address TEXT NOT NULL,
             PRIMARY KEY (contact, email_address)
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migratePhoneNumbersTable(database: SupportSQLiteDatabase) {
        val tempTableName = "PhoneNumbersTemp"
        val originalTableName = "PhoneNumbers"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             contact TEXT NOT NULL, 
             phone_number TEXT NOT NULL,
             PRIMARY KEY (contact, phone_number))
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateMessageDeletionTable(database: SupportSQLiteDatabase) {
        val tempTableName = "MsgDeletionTemp"
        val originalTableName = "MsgDeletion"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             message_id TEXT NOT NULL, 
             timestamp INTEGER NOT NULL, 
             PRIMARY KEY (message_id))""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateEditHistoryTable(database: SupportSQLiteDatabase) {
        val tempTableName = "EditHistoryTemp"
        val originalTableName = "EditHistory"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             original_id TEXT NOT NULL, 
             updated_id TEXT NOT NULL, 
             timestamp INTEGER NOT NULL, 
             PRIMARY KEY (original_id)
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateMessageContentIndexTable(database: SupportSQLiteDatabase) {
        val tempTableName = "MessageContentIndexTemp"
        val originalTableName = "MessageContentIndex"
        val createTempTable = """
             CREATE VIRTUAL TABLE $tempTableName using fts3(
             message_id TEXT NOT NULL, 
             conv_id TEST NOT NULL, 
             content TEST NOT NULL, 
             time INTEGER NOT NULL,
             PRIMARY KEY (message_id)
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migratePushNotificationEvents(database: SupportSQLiteDatabase) {
        val tempTableName = "PushNotificationEventsTemp"
        val originalTableName = "PushNotificationEvents"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             pushId TEXT NOT NULL, 
             event_index INTEGER NOT NULL, 
             decrypted INTEGER NOT NULL, 
             event TEXT NOT NULL, 
             plain BLOB, 
             transient INTEGER NOT NULL, 
             PRIMARY KEY (event_index)
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateReadRecieptsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ReadReceiptsTemp"
        val originalTableName = "ReadReceipts"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             message_id TEXT NOT NULL, 
             user_id TEXT NOT NULL, 
             timestamp INTEGER NOT NULL, 
             PRIMARY KEY (message_id, user_id)
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migratePropertiesTable(database: SupportSQLiteDatabase) {
        val tempTableName = "PropertiesTemp"
        val originalTableName = "Properties"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             key TEXT NOT NULL, 
             value TEXT NOT NULL, 
             PRIMARY KEY (key)
             )""".trimIndent()

        executeSimpleMigration(
            database = database,
            originalTableName = originalTableName,
            tempTableName = tempTableName,
            createTempTable = createTempTable
        )
    }

    private fun migrateUploadAssetTable(database: SupportSQLiteDatabase) {
        val tempTableName = "UploadAssetsTemp"
        val originalTableName = "UploadAssets"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             _id TEXT NOT NULL, 
             source TEXT NOT NULL, 
             name TEXT NOT NULL, 
             sha BLOB NOT NULL, 
             md5 BLOB NOT NULL, 
             mime TEXT NOT NULL, 
             preview TEXT NOT NULL, 
             uploaded INTEGER NOT NULL, 
             size INTEGER NOT NULL, 
             retention INTEGER NOT NULL, 
             public INTEGER NOT NULL, 
             encryption TEXT NOT NULL, 
             encryption_salt TEXT, 
             details TEXT NOT NULL, 
             status INTEGER NOT NULL, 
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
