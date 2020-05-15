package com.waz.zclient.storage.userdatabase

import com.waz.zclient.storage.DbSQLiteOpenHelper


class UserDatabaseHelper {

    private val userTableQuery = """ CREATE TABLE IF NOT EXISTS Users (_id TEXT PRIMARY KEY,teamId TEXT, name TEXT, email TEXT, phone TEXT, tracking_id TEXT,picture TEXT, accent INTEGER, skey TEXT, connection TEXT, conn_timestamp INTEGER,conn_msg TEXT, conversation TEXT, relation TEXT, timestamp INTEGER,verified TEXT, deleted INTEGER, availability INTEGER,handle TEXT, provider_id TEXT, integration_id TEXT, expires_at INTEGER,managed_by TEXT, self_permissions INTEGER, copy_permissions INTEGER, created_by TEXT)""".trimIndent()
    private val assetsTableQuery = """CREATE TABLE IF NOT EXISTS Assets (_id TEXT PRIMARY KEY, asset_type TEXT , data TEXT )""".trimIndent()
    private val conversationsTableQuery = """CREATE TABLE IF NOT EXISTS Conversations (_id TEXT PRIMARY KEY, remote_id TEXT , name TEXT , creator TEXT , conv_type INTEGER , team TEXT , is_managed INTEGER , last_event_time INTEGER , is_active INTEGER , last_read INTEGER , muted_status INTEGER , mute_time INTEGER , archived INTEGER , archive_time INTEGER , cleared INTEGER , generated_name TEXT , search_key TEXT , unread_count INTEGER , unsent_count INTEGER , hidden INTEGER , missed_call TEXT , incoming_knock TEXT , verified TEXT , ephemeral INTEGER , global_ephemeral INTEGER , unread_call_count INTEGER , unread_ping_count INTEGER , access TEXT , access_role TEXT , link TEXT , unread_mentions_count INTEGER , unread_quote_count INTEGER , receipt_mode INTEGER )""".trimIndent()
    private val conversationMembersTableQuery = """CREATE TABLE IF NOT EXISTS ConversationMembers (user_id TEXT , conv_id TEXT , role TEXT , PRIMARY KEY (user_id, conv_id))""".trimIndent()
    private val messagesTableQuery = """CREATE TABLE IF NOT EXISTS Messages (_id TEXT PRIMARY KEY, conv_id TEXT , msg_type TEXT , user_id TEXT , content TEXT , protos BLOB , time INTEGER , local_time INTEGER , first_msg INTEGER , members TEXT , recipient TEXT , email TEXT , name TEXT , msg_state TEXT , content_size INTEGER , edit_time INTEGER , ephemeral INTEGER , expiry_time INTEGER , expired INTEGER , duration INTEGER , quote TEXT , quote_validity INTEGER , force_read_receipts INTEGER , asset_id TEXT )""".trimIndent()
    private val keyValuesTableQuery = """CREATE TABLE IF NOT EXISTS KeyValues (key TEXT PRIMARY KEY, value TEXT )""".trimIndent()
    private val syncJobsTableQuery = """CREATE TABLE IF NOT EXISTS SyncJobs (_id TEXT PRIMARY KEY, data TEXT )""".trimIndent()
    private val errorsTableQuery = """CREATE TABLE IF NOT EXISTS Errors (_id TEXT PRIMARY KEY, err_type TEXT , users TEXT , messages TEXT , conv_id TEXT , res_code INTEGER , res_msg TEXT , res_label TEXT , time INTEGER )""".trimIndent()
    private val notificationDataTableQuery = """CREATE TABLE IF NOT EXISTS NotificationData (_id TEXT PRIMARY KEY, data TEXT )""".trimIndent()
    private val contactHashesTableQuery = """CREATE TABLE IF NOT EXISTS ContactHashes (_id TEXT PRIMARY KEY, hashes TEXT )""".trimIndent()
    private val contactsOnWireTableQuery = """CREATE TABLE IF NOT EXISTS ContactsOnWire (user TEXT , contact TEXT , PRIMARY KEY (user, contact))""".trimIndent()
    private val clientsTableQuery = """CREATE TABLE IF NOT EXISTS Clients (_id TEXT PRIMARY KEY, data TEXT )""".trimIndent()
    private val likingsTableQuery = """CREATE TABLE IF NOT EXISTS Likings (message_id TEXT , user_id TEXT , timestamp INTEGER , action INTEGER , PRIMARY KEY (message_id, user_id))""".trimIndent()
    private val contactsTableQuery = """CREATE TABLE IF NOT EXISTS Contacts (_id TEXT PRIMARY KEY, name TEXT , name_source INTEGER , sort_key TEXT , search_key TEXT )""".trimIndent()
    private val emailAddressesTableQuery = """CREATE TABLE IF NOT EXISTS EmailAddresses (contact TEXT , email_address TEXT )""".trimIndent()
    private val phoneNumbersTableQuery = """CREATE TABLE IF NOT EXISTS PhoneNumbers (contact TEXT , phone_number TEXT )""".trimIndent()
    private val msgDeletionTableQuery = """CREATE TABLE IF NOT EXISTS MsgDeletion (message_id TEXT , timestamp INTEGER , PRIMARY KEY (message_id))""".trimIndent()
    private val editHistoryTableQuery = """CREATE TABLE IF NOT EXISTS EditHistory (original_id TEXT , updated_id TEXT , timestamp INTEGER , PRIMARY KEY (original_id))""".trimIndent()
    private val pushNotificationEventsTableQuery = """CREATE TABLE IF NOT EXISTS PushNotificationEvents (pushId TEXT , event_index INTEGER , decrypted INTEGER , event TEXT , plain BLOB , transient INTEGER , PRIMARY KEY (event_index))""".trimIndent()
    private val readReceiptsTableQuery = """CREATE TABLE IF NOT EXISTS ReadReceipts (message_id TEXT , user_id TEXT , timestamp INTEGER , PRIMARY KEY (message_id, user_id))""".trimIndent()
    private val propertiesTableQuery = """CREATE TABLE IF NOT EXISTS Properties (key TEXT , value TEXT , PRIMARY KEY (key))""".trimIndent()
    private val uploadAssetsTableQuery = """CREATE TABLE IF NOT EXISTS UploadAssets (_id TEXT , source TEXT , name TEXT , sha BLOB , md5 BLOB , mime TEXT , preview TEXT , uploaded INTEGER , size INTEGER , retention INTEGER , public INTEGER , encryption TEXT , encryption_salt TEXT , details TEXT , status INTEGER , asset_id TEXT , PRIMARY KEY (_id))""".trimIndent()
    private val downloadAssetsTableQuery = """CREATE TABLE IF NOT EXISTS DownloadAssets (_id TEXT , mime TEXT , name TEXT , preview TEXT , details TEXT , downloaded INTEGER , size INTEGER , status INTEGER , PRIMARY KEY (_id))""".trimIndent()
    private val assets2TableQuery = """CREATE TABLE IF NOT EXISTS Assets2 (_id TEXT , token TEXT , name TEXT , encryption TEXT , mime TEXT , sha BLOB , size INTEGER , source TEXT , preview TEXT , details TEXT , conversation_id TEXT , PRIMARY KEY (_id))""".trimIndent()
    private val fcmNotificationsTableQuery = """CREATE TABLE IF NOT EXISTS FCMNotifications (_id TEXT , stage TEXT , stage_start_time INTEGER , PRIMARY KEY (_id, stage))""".trimIndent()
    private val fcmNotificationStatsTableQuery = """CREATE TABLE IF NOT EXISTS FCMNotificationStats (stage TEXT PRIMARY KEY, bucket1 INTEGER , bucket2 INTEGER , bucket3 INTEGER )""".trimIndent()
    private val foldersTableQuery = """CREATE TABLE IF NOT EXISTS Folders (_id TEXT PRIMARY KEY, name TEXT , type INTEGER )""".trimIndent()
    private val conversationFoldersTableQuery = """CREATE TABLE IF NOT EXISTS ConversationFolders (conv_id TEXT , folder_id TEXT , PRIMARY KEY (conv_id, folder_id))""".trimIndent()
    private val conversationRoleActionTableQuery = """CREATE TABLE IF NOT EXISTS ConversationRoleAction (label TEXT , action TEXT , conv_id TEXT , PRIMARY KEY (label, action, conv_id))""".trimIndent()
    private val messageContentIndexQuery = """CREATE VIRTUAL TABLE MessageContentIndex using fts4(message_id TEXT, conv_id TEXT, content TEXT, time INTEGER)""".trimIndent()

    private val createQueries = arrayOf(
        userTableQuery, assetsTableQuery, conversationsTableQuery,
        conversationMembersTableQuery, messagesTableQuery, keyValuesTableQuery,
        syncJobsTableQuery, errorsTableQuery, notificationDataTableQuery,
        contactHashesTableQuery, contactsOnWireTableQuery, contactsOnWireTableQuery,
        clientsTableQuery, likingsTableQuery, contactsTableQuery, emailAddressesTableQuery,
        phoneNumbersTableQuery, msgDeletionTableQuery, editHistoryTableQuery,
        pushNotificationEventsTableQuery, readReceiptsTableQuery, propertiesTableQuery,
        uploadAssetsTableQuery, downloadAssetsTableQuery, assets2TableQuery,
        fcmNotificationsTableQuery, fcmNotificationStatsTableQuery, foldersTableQuery,
        conversationFoldersTableQuery, conversationRoleActionTableQuery, messageContentIndexQuery
    )

    fun createDatabase(openHelper: DbSQLiteOpenHelper) {
        with(openHelper) {
            createQueries.forEach { execSQL(it) }
        }
    }

    fun clearDatabase(openHelper: DbSQLiteOpenHelper) {
        with(openHelper) {
            val getAllTablesQuery = "SELECT name FROM sqlite_master WHERE type='table'"
            val cursor = writableDatabase.rawQuery(getAllTablesQuery, null)

            val tables = mutableListOf<String>()
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }

            tables.forEach {
                execSQL("DROP TABLE IF EXISTS $it")
            }

            close()
        }
    }
}
