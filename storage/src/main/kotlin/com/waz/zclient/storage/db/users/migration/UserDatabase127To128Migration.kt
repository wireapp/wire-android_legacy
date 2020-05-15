@file:Suppress("MagicNumber", "TooManyFunctions", "LargeClass")
package com.waz.zclient.storage.db.users.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.storage.db.users.migration.MigrationUtils.recreateAndTryMigrate

val USER_DATABASE_MIGRATION_127_TO_128 = object : Migration(127, 128) {
    override fun migrate(database: SupportSQLiteDatabase) {
        migrateUserTable(database)
        migrateAssetsTable(database)
        migrateConversationsTable(database)
        migrateConversationMembersTable(database)
        migrateMessagesTable(database)
        migrateKeyValuesTable(database)
        migrateSyncJobsTable(database)
        migrateErrorsTable(database)
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
        migratePushNotificationEvents(database)
        migrateReadReceiptsTable(database)
        migratePropertiesTable(database)
        migrateUploadAssetTable(database)
        migrateDownloadAssetTable(database)
        migrateAssets2Table(database)
        migrateFcmNotificationsTable(database)
        migrateFcmNotificationStatsTable(database)
        migrateFoldersTable(database)
        migrateConversationFoldersTable(database)
        migrateConversationRoleActionTable(database)
        migrateMessageContentIndexTable(database)

        createButtonsTable(database)
    }

    private fun migrateUserTable(database: SupportSQLiteDatabase) {
        val tempTableName = "UsersTemp"
        val originalTableName = "Users"
        val primaryKey = "_id"
        val searchKey = "skey"
        val createTempTable = """
        CREATE TABLE $tempTableName (
            $primaryKey TEXT PRIMARY KEY NOT NULL,
            teamId TEXT,
            name TEXT NOT NULL DEFAULT '',
            email TEXT, 
            phone TEXT, 
            tracking_id TEXT, 
            picture TEXT, 
            accent INTEGER NOT NULL DEFAULT 0, 
            $searchKey TEXT NOT NULL DEFAULT '', 
            connection TEXT NOT NULL DEFAULT '', 
            conn_timestamp INTEGER NOT NULL DEFAULT 0, 
            conn_msg TEXT, 
            conversation TEXT, 
            relation TEXT NOT NULL DEFAULT '', 
            timestamp INTEGER, 
            verified TEXT, 
            deleted INTEGER NOT NULL DEFAULT 0, 
            availability INTEGER NOT NULL DEFAULT 0, 
            handle TEXT, 
            provider_id TEXT, 
            integration_id TEXT, 
            expires_at INTEGER, 
            managed_by TEXT, 
            self_permissions INTEGER NOT NULL DEFAULT 0, 
            copy_permissions INTEGER NOT NULL DEFAULT 0, 
            created_by TEXT 
       )""".trimIndent()

        val conversationIdIndex = "CREATE INDEX IF NOT EXISTS Conversation_id on $originalTableName ($primaryKey)"
        val searchKeyIndex = "CREATE INDEX IF NOT EXISTS UserData_search_key on $originalTableName ($searchKey)"
        recreateAndTryMigrate(
                database,
                originalTableName,
                tempTableName,
                createTempTable,
                conversationIdIndex,
                searchKeyIndex
        )
    }

    private fun migrateAssetsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "AssetsTemp"
        val originalTableName = "Assets"
        val createTempTable = """
                CREATE TABLE $tempTableName (
                _id TEXT PRIMARY KEY NOT NULL,
                asset_type TEXT NOT NULL DEFAULT '',
                data TEXT NOT NULL DEFAULT ''
                )""".trimIndent()

        recreateAndTryMigrate(
                database = database,
                originalTableName = originalTableName,
                tempTableName = tempTableName,
                createTempTable = createTempTable
        )
    }

    private fun migrateConversationsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ConversationsTemp"
        val originalTableName = "Conversations"
        val searchKey = "search_key"
        val createTempTable = """
                CREATE TABLE $tempTableName (
                _id TEXT PRIMARY KEY NOT NULL,
                remote_id TEXT NOT NULL DEFAULT '',
                name TEXT,
                creator TEXT NOT NULL DEFAULT '',
                conv_type INTEGER NOT NULL DEFAULT 0,
                team TEXT,
                is_managed INTEGER,
                last_event_time INTEGER NOT NULL DEFAULT 0,
                is_active INTEGER NOT NULL DEFAULT 0,
                last_read INTEGER NOT NULL DEFAULT 0,
                muted_status INTEGER NOT NULL DEFAULT 0,
                mute_time INTEGER NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0,
                archive_time INTEGER NOT NULL DEFAULT 0,
                cleared INTEGER,
                generated_name TEXT NOT NULL DEFAULT '',
                $searchKey TEXT, 
                unread_count INTEGER NOT NULL DEFAULT 0, 
                unsent_count INTEGER NOT NULL DEFAULT 0, 
                hidden INTEGER NOT NULL DEFAULT 0, 
                missed_call TEXT,
                incoming_knock TEXT, 
                verified TEXT, 
                ephemeral INTEGER,
                global_ephemeral INTEGER,
                unread_call_count INTEGER NOT NULL DEFAULT 0,
                unread_ping_count INTEGER NOT NULL DEFAULT 0,
                access TEXT, 
                access_role TEXT, 
                link TEXT, 
                unread_mentions_count INTEGER NOT NULL DEFAULT 0, 
                unread_quote_count INTEGER NOT NULL DEFAULT 0, 
                receipt_mode INTEGER 
                )""".trimIndent()
        val conversationSearchKeyIndex = """
            CREATE INDEX IF NOT EXISTS Conversation_search_key on $originalTableName ($searchKey)
            """.trimIndent()
        recreateAndTryMigrate(
                database,
                originalTableName,
                tempTableName,
                createTempTable,
                conversationSearchKeyIndex
        )
    }

    private fun migrateConversationMembersTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ConversationMembersTemp"
        val originalTableName = "ConversationMembers"
        val convid = "conv_id"
        val userId = "user_id"
        val createTempTable = """
                CREATE TABLE $tempTableName (
                $userId TEXT NOT NULL DEFAULT '', 
                $convid TEXT NOT NULL DEFAULT '', 
                role TEXT NOT NULL DEFAULT '',
                PRIMARY KEY ($userId, $convid));
                )""".trimIndent()

        val conversationIdIndex = "CREATE INDEX IF NOT EXISTS ConversationMembers_conv on $originalTableName ($convid)"
        val userIdIndex = "CREATE INDEX IF NOT EXISTS ConversationMembers_userid on $originalTableName ($userId)"

        recreateAndTryMigrate(
                database,
                originalTableName,
                tempTableName,
                createTempTable,
                conversationIdIndex,
                userIdIndex
        )
    }

    private fun migrateMessagesTable(database: SupportSQLiteDatabase) {
        val tempTableName = "MessagesTemp"
        val originalTableName = "Messages"
        val convId = "conv_id"
        val time = "time"
        val createTempTable = """
                CREATE TABLE $tempTableName (
                _id TEXT PRIMARY KEY NOT NULL,
                $convId TEXT NOT NULL DEFAULT '',
                msg_type TEXT NOT NULL DEFAULT '', 
                user_id TEXT NOT NULL DEFAULT '',
                content TEXT,
                protos BLOB, 
                $time INTEGER NOT NULL DEFAULT 0, 
                local_time INTEGER NOT NULL DEFAULT 0, 
                first_msg INTEGER NOT NULL DEFAULT 0,
                members TEXT, 
                recipient TEXT, 
                email TEXT,
                name TEXT,
                msg_state TEXT NOT NULL DEFAULT '',
                content_size INTEGER NOT NULL DEFAULT 0,
                edit_time INTEGER NOT NULL DEFAULT 0,
                ephemeral INTEGER,
                expiry_time INTEGER,
                expired INTEGER NOT NULL DEFAULT 0,
                duration INTEGER,
                quote TEXT,
                quote_validity INTEGER NOT NULL DEFAULT 0,
                force_read_receipts INTEGER,
                asset_id TEXT
               )""".trimIndent()
        val convAndTimeIndex = "CREATE INDEX IF NOT EXISTS Messages_conv_time on $originalTableName ($convId, $time)"

        recreateAndTryMigrate(
                database,
                originalTableName,
                tempTableName,
                createTempTable,
                convAndTimeIndex
        )
    }

    private fun migrateKeyValuesTable(database: SupportSQLiteDatabase) {
        val tempTableName = "KeyValuesTemp"
        val originalTableName = "KeyValues"
        val createTempTable = """
                CREATE TABLE $tempTableName (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL DEFAULT ''
               )""".trimIndent()

        recreateAndTryMigrate(
                database = database,
                originalTableName = originalTableName,
                tempTableName = tempTableName,
                createTempTable = createTempTable
        )
    }

    private fun migrateSyncJobsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "SyncJobsTemp"
        val originalTableName = "SyncJobs"
        val createTempTable = """
                CREATE TABLE $tempTableName (
                _id TEXT PRIMARY KEY NOT NULL,
                data TEXT NOT NULL DEFAULT ''
               )""".trimIndent()

        recreateAndTryMigrate(
                database = database,
                originalTableName = originalTableName,
                tempTableName = tempTableName,
                createTempTable = createTempTable
        )
    }

    private fun migrateErrorsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ErrorsTemp"
        val originalTableName = "Errors"
        val createTempTable = """
                CREATE TABLE $tempTableName (
                _id TEXT PRIMARY KEY NOT NULL,
                err_type TEXT NOT NULL DEFAULT '',
                users TEXT NOT NULL DEFAULT '', 
                messages TEXT NOT NULL DEFAULT '', 
                conv_id TEXT, 
                res_code INTEGER NOT NULL DEFAULT 0, 
                res_msg TEXT NOT NULL DEFAULT '', 
                res_label TEXT NOT NULL DEFAULT '', 
                time INTEGER NOT NULL DEFAULT 0
                )""".trimIndent()

        recreateAndTryMigrate(
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
            data TEXT NOT NULL DEFAULT ''
            )""".trimIndent()

        recreateAndTryMigrate(
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
             hashes TEXT
             )""".trimIndent()

        recreateAndTryMigrate(
                database = database,
                originalTableName = originalTableName,
                tempTableName = tempTableName,
                createTempTable = createTempTable
        )
    }

    private fun migrateContactsOnWire(database: SupportSQLiteDatabase) {
        val tempTableName = "ContactsOnWireTemp"
        val originalTableName = "ContactsOnWire"
        val contact = "contact"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             user TEXT NOT NULL DEFAULT '', 
             $contact TEXT NOT NULL DEFAULT '', 
             PRIMARY KEY (user, contact)
             )""".trimIndent()

        val contactIndex = "CREATE INDEX IF NOT EXISTS ContactsOnWire_contact on $originalTableName ( $contact )"

        recreateAndTryMigrate(
                database,
                originalTableName,
                tempTableName,
                createTempTable,
                contactIndex
        )
    }

    private fun migrateClientsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ClientsTemp"
        val originalTableName = "Clients"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             _id TEXT PRIMARY KEY NOT NULL, 
             data TEXT NOT NULL DEFAULT ''
             )""".trimIndent()

        recreateAndTryMigrate(
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
             message_id TEXT NOT NULL DEFAULT '', 
             user_id TEXT NOT NULL DEFAULT '', 
             timestamp INTEGER NOT NULL DEFAULT 0, 
             action INTEGER NOT NULL DEFAULT 0, 
             PRIMARY KEY (message_id, user_id)
             )""".trimIndent()

        recreateAndTryMigrate(
                database = database,
                originalTableName = originalTableName,
                tempTableName = tempTableName,
                createTempTable = createTempTable
        )
    }

    private fun migrateContactsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ContactsTemp"
        val originalTableName = "Contacts"
        val sorting = "sort_key"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             _id TEXT PRIMARY KEY NOT NULL, 
             name TEXT NOT NULL DEFAULT '', 
             name_source INTEGER NOT NULL DEFAULT 0, 
             $sorting TEXT NOT NULL DEFAULT '', 
             search_key TEXT NOT NULL DEFAULT ''
             )""".trimIndent()

        val contactSortingIndex = "CREATE INDEX IF NOT EXISTS Contacts_sorting on $originalTableName ( $sorting )"

        recreateAndTryMigrate(
                database,
                originalTableName,
                tempTableName,
                createTempTable,
                contactSortingIndex
        )
    }

    private fun migrateEmailAddressTable(database: SupportSQLiteDatabase) {
        val tempTableName = "EmailAddressesTemp"
        val originalTableName = "EmailAddresses"
        val contact = "contact"
        val emailAddress = "email_address"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             $contact TEXT NOT NULL DEFAULT '', 
             $emailAddress TEXT NOT NULL DEFAULT '',
             PRIMARY KEY (contact, email_address)
             )""".trimIndent()

        val contactIndex = "CREATE INDEX IF NOT EXISTS EmailAddresses_contact on EmailAddresses ($contact)"
        val emailIndex = "CREATE INDEX IF NOT EXISTS EmailAddresses_email on EmailAddresses ($emailAddress)"

        recreateAndTryMigrate(
                database,
                originalTableName,
                tempTableName,
                createTempTable,
                contactIndex,
                emailIndex
        )
    }

    private fun migratePhoneNumbersTable(database: SupportSQLiteDatabase) {
        val tempTableName = "PhoneNumbersTemp"
        val originalTableName = "PhoneNumbers"
        val contact = "contact"
        val phoneNumber = "phone_number"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             $contact TEXT NOT NULL DEFAULT '', 
             $phoneNumber TEXT NOT NULL DEFAULT '',
             PRIMARY KEY (contact, phone_number)
             )""".trimIndent()

        val contactIndex = "CREATE INDEX IF NOT EXISTS PhoneNumbers_contact on $originalTableName ($contact)"
        val phoneIndex = "CREATE INDEX IF NOT EXISTS PhoneNumbers_phone on $originalTableName ($phoneNumber)"

        recreateAndTryMigrate(
                database,
                originalTableName,
                tempTableName,
                createTempTable,
                contactIndex,
                phoneIndex
        )
    }

    private fun migrateMessageDeletionTable(database: SupportSQLiteDatabase) {
        val tempTableName = "MsgDeletionTemp"
        val originalTableName = "MsgDeletion"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             message_id TEXT PRIMARY KEY NOT NULL, 
             timestamp INTEGER NOT NULL DEFAULT 0
             )""".trimIndent()

        recreateAndTryMigrate(
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
             original_id TEXT PRIMARY KEY NOT NULL, 
             updated_id TEXT NOT NULL DEFAULT '', 
             timestamp INTEGER NOT NULL DEFAULT 0
             )""".trimIndent()

        recreateAndTryMigrate(
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
             pushId TEXT PRIMARY KEY NOT NULL,
             event_index INTEGER NOT NULL DEFAULT 0, 
             decrypted INTEGER NOT NULL DEFAULT 0, 
             event TEXT NOT NULL DEFAULT '', 
             plain BLOB, 
             transient INTEGER NOT NULL DEFAULT 0
             )""".trimIndent()

        recreateAndTryMigrate(
                database = database,
                originalTableName = originalTableName,
                tempTableName = tempTableName,
                createTempTable = createTempTable
        )
    }

    private fun migrateReadReceiptsTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ReadReceiptsTemp"
        val originalTableName = "ReadReceipts"
        val createTempTable = """
             CREATE TABLE $tempTableName (
             message_id TEXT NOT NULL DEFAULT '', 
             user_id TEXT NOT NULL DEFAULT '', 
             timestamp INTEGER NOT NULL DEFAULT 0, 
             PRIMARY KEY (message_id, user_id)
             )""".trimIndent()

        recreateAndTryMigrate(
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
             key TEXT PRIMARY KEY NOT NULL, 
             value TEXT NOT NULL DEFAULT ''
             )""".trimIndent()

        recreateAndTryMigrate(
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
             _id TEXT PRIMARY KEY NOT NULL, 
             source TEXT NOT NULL DEFAULT '', 
             name TEXT NOT NULL DEFAULT '', 
             sha BLOB, 
             md5 BLOB, 
             mime TEXT NOT NULL DEFAULT '', 
             preview TEXT NOT NULL DEFAULT '', 
             uploaded INTEGER NOT NULL DEFAULT 0, 
             size INTEGER NOT NULL DEFAULT 0, 
             retention INTEGER NOT NULL DEFAULT 0, 
             public INTEGER NOT NULL DEFAULT 0, 
             encryption TEXT NOT NULL DEFAULT '', 
             encryption_salt TEXT, 
             details TEXT NOT NULL DEFAULT '', 
             status INTEGER NOT NULL DEFAULT 0, 
             asset_id TEXT
             )""".trimIndent()

        recreateAndTryMigrate(
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
              _id TEXT PRIMARY KEY NOT NULL, 
              mime TEXT NOT NULL DEFAULT '', 
              name TEXT NOT NULL DEFAULT '', 
              preview TEXT NOT NULL DEFAULT '', 
              details TEXT NOT NULL DEFAULT '', 
              downloaded INTEGER NOT NULL DEFAULT 0, 
              size INTEGER NOT NULL DEFAULT 0, 
              status INTEGER NOT NULL DEFAULT 0
              )""".trimIndent()

        recreateAndTryMigrate(
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
              _id TEXT PRIMARY KEY NOT NULL, 
              token TEXT, 
              name TEXT NOT NULL DEFAULT '', 
              encryption TEXT NOT NULL DEFAULT '', 
              mime TEXT NOT NULL DEFAULT '', 
              sha BLOB, 
              size INTEGER NOT NULL DEFAULT 0, 
              source TEXT, 
              preview TEXT, 
              details TEXT NOT NULL DEFAULT '', 
              conversation_id TEXT
              )""".trimIndent()

        recreateAndTryMigrate(
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
              _id TEXT NOT NULL DEFAULT '', 
              stage TEXT NOT NULL DEFAULT '', 
              stage_start_time INTEGER NOT NULL DEFAULT 0, 
              PRIMARY KEY (_id, stage)
              )""".trimIndent()

        recreateAndTryMigrate(
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
              bucket1 INTEGER NOT NULL DEFAULT 0, 
              bucket2 INTEGER NOT NULL DEFAULT 0, 
              bucket3 INTEGER NOT NULL DEFAULT 0
              )""".trimIndent()

        recreateAndTryMigrate(
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
              name TEXT NOT NULL DEFAULT '', 
              type INTEGER NOT NULL DEFAULT 0
              )""".trimIndent()

        recreateAndTryMigrate(
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
              conv_id TEXT NOT NULL DEFAULT '', 
              folder_id TEXT NOT NULL DEFAULT '', 
              PRIMARY KEY (conv_id, folder_id)
              )""".trimIndent()

        recreateAndTryMigrate(
                database = database,
                originalTableName = originalTableName,
                tempTableName = tempTableName,
                createTempTable = createTempTable
        )
    }

    private fun migrateConversationRoleActionTable(database: SupportSQLiteDatabase) {
        val tempTableName = "ConversationRoleActionTemp"
        val originalTableName = "ConversationRoleAction"
        val convId = "conv_id"
        val createTempTable = """
               CREATE TABLE $tempTableName (
               label TEXT NOT NULL DEFAULT '', 
               action TEXT NOT NULL DEFAULT '', 
               $convId TEXT NOT NULL DEFAULT '', 
               PRIMARY KEY (label, action, $convId)
               )""".trimIndent()

        val conversationIdIndex = """
            CREATE INDEX IF NOT EXISTS ConversationRoleAction_convid on $originalTableName ($convId)
            """.trimIndent()

        recreateAndTryMigrate(
                database,
                originalTableName,
                tempTableName,
                createTempTable,
                conversationIdIndex
        )
    }

    private fun migrateMessageContentIndexTable(database: SupportSQLiteDatabase) {
        val tempTableName = "MessageContentIndexTemp"
        val originalTableName = "MessageContentIndex"
        val messageId = "message_id"
        val convId = "conv_id"
        val content = "content"
        val time = "time"
        val createTempTable = """
              CREATE VIRTUAL TABLE $tempTableName using fts4(
              $messageId TEXT NOT NULL DEFAULT '',
              $convId TEXT NOT NULL DEFAULT '',
              $content TEXT NOT NULL DEFAULT '',
              $time INTEGER NOT NULL DEFAULT 0,
              )""".trimIndent()

        recreateAndTryMigrate(
                database = database,
                originalTableName = originalTableName,
                tempTableName = tempTableName,
                createTempTable = createTempTable
        )
    }

    private fun createButtonsTable(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS Buttons")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS Buttons (
                message_id TEXT NOT NULL DEFAULT '', 
                button_id TEXT NOT NULL DEFAULT '', 
                title TEXT NOT NULL DEFAULT '',
                ordinal INTEGER NOT NULL DEFAULT 0,
                state INTEGER NOT NULL DEFAULT 0, 
                PRIMARY KEY(message_id, button_id)
            )""".trimIndent()
        )
    }
}
