package com.waz.zclient.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.waz.zclient.storage.db.assets.AssetsDao
import com.waz.zclient.storage.db.assets.AssetsEntity
import com.waz.zclient.storage.db.assets.AssetsV1Dao
import com.waz.zclient.storage.db.assets.AssetsV1Entity
import com.waz.zclient.storage.db.assets.DownloadAssetsDao
import com.waz.zclient.storage.db.assets.DownloadAssetsEntity
import com.waz.zclient.storage.db.assets.UploadAssetsDao
import com.waz.zclient.storage.db.assets.UploadAssetsEntity
import com.waz.zclient.storage.db.buttons.ButtonsDao
import com.waz.zclient.storage.db.buttons.ButtonsEntity
import com.waz.zclient.storage.db.conversations.ConversationFoldersDao
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity
import com.waz.zclient.storage.db.conversations.ConversationMembersDao
import com.waz.zclient.storage.db.conversations.ConversationMembersEntity
import com.waz.zclient.storage.db.conversations.ConversationRoleActionDao
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity
import com.waz.zclient.storage.db.conversations.ConversationsDao
import com.waz.zclient.storage.db.conversations.ConversationsEntity
import com.waz.zclient.storage.db.errors.ErrorsDao
import com.waz.zclient.storage.db.errors.ErrorsEntity
import com.waz.zclient.storage.db.folders.FoldersDao
import com.waz.zclient.storage.db.folders.FoldersEntity
import com.waz.zclient.storage.db.history.EditHistoryDao
import com.waz.zclient.storage.db.history.EditHistoryEntity
import com.waz.zclient.storage.db.messages.LikesDao
import com.waz.zclient.storage.db.messages.LikesEntity
import com.waz.zclient.storage.db.messages.MessageContentIndexDao
import com.waz.zclient.storage.db.messages.MessageContentIndexEntity
import com.waz.zclient.storage.db.messages.MessageDeletionEntity
import com.waz.zclient.storage.db.messages.MessagesDao
import com.waz.zclient.storage.db.messages.MessagesDeletionDao
import com.waz.zclient.storage.db.messages.MessagesEntity
import com.waz.zclient.storage.db.notifications.CloudNotificationStatsDao
import com.waz.zclient.storage.db.notifications.CloudNotificationStatsEntity
import com.waz.zclient.storage.db.notifications.CloudNotificationsDao
import com.waz.zclient.storage.db.notifications.CloudNotificationsEntity
import com.waz.zclient.storage.db.notifications.NotificationDataDao
import com.waz.zclient.storage.db.notifications.NotificationDataEntity
import com.waz.zclient.storage.db.notifications.PushNotificationEventDao
import com.waz.zclient.storage.db.notifications.PushNotificationEventEntity
import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity
import com.waz.zclient.storage.db.property.PropertiesDao
import com.waz.zclient.storage.db.property.PropertiesEntity
import com.waz.zclient.storage.db.receipts.ReadReceiptsDao
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity
import com.waz.zclient.storage.db.sync.SyncJobsDao
import com.waz.zclient.storage.db.sync.SyncJobsEntity
import com.waz.zclient.storage.db.userclients.UserClientDao
import com.waz.zclient.storage.db.userclients.UserClientsEntity
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_128_TO_129
import com.waz.zclient.storage.db.users.model.UsersEntity
import com.waz.zclient.storage.db.users.service.UsersDao

@Database(
    entities = [UsersEntity::class, AssetsV1Entity::class, ConversationsEntity::class,
        ConversationMembersEntity::class, MessagesEntity::class, KeyValuesEntity::class,
        SyncJobsEntity::class, ErrorsEntity::class, NotificationDataEntity::class,
        UserClientsEntity::class,
        LikesEntity::class,
        MessageDeletionEntity::class, ConversationRoleActionEntity::class,
        ConversationFoldersEntity::class, FoldersEntity::class, CloudNotificationStatsEntity::class,
        CloudNotificationsEntity::class, AssetsEntity::class, DownloadAssetsEntity::class,
        UploadAssetsEntity::class, PropertiesEntity::class, ReadReceiptsEntity::class,
        PushNotificationEventEntity::class, EditHistoryEntity::class, ButtonsEntity::class,
        MessageContentIndexEntity::class],
    version = UserDatabase.VERSION
)

@Suppress("TooManyFunctions")
abstract class UserDatabase : RoomDatabase() {

    abstract fun usersDao(): UsersDao
    abstract fun userClientDao(): UserClientDao
    abstract fun assetsV1Dao(): AssetsV1Dao
    abstract fun assetsDao(): AssetsDao
    abstract fun downloadAssetsDao(): DownloadAssetsDao
    abstract fun uploadAssetsDao(): UploadAssetsDao
    abstract fun syncJobsDao(): SyncJobsDao
    abstract fun errorsDao(): ErrorsDao
    abstract fun messagesDao(): MessagesDao
    abstract fun messagesDeletionDao(): MessagesDeletionDao
    abstract fun likesDao(): LikesDao
    abstract fun conversationFoldersDao(): ConversationFoldersDao
    abstract fun conversationMembersDao(): ConversationMembersDao
    abstract fun conversationRoleActionDao(): ConversationRoleActionDao
    abstract fun conversationsDao(): ConversationsDao
    abstract fun keyValuesDao(): KeyValuesDao
    abstract fun propertiesDao(): PropertiesDao
    abstract fun cloudNotificationsDao(): CloudNotificationsDao
    abstract fun cloudNotificationStatsDao(): CloudNotificationStatsDao
    abstract fun notificationDataDao(): NotificationDataDao
    abstract fun pushNotificationEventDao(): PushNotificationEventDao
    abstract fun foldersDao(): FoldersDao
    abstract fun readReceiptsDao(): ReadReceiptsDao
    abstract fun editHistoryDao(): EditHistoryDao
    abstract fun messageContentIndexDao(): MessageContentIndexDao
    abstract fun buttonsDao(): ButtonsDao

    companion object {
        const val VERSION = 129

        @JvmStatic
        val migrations = arrayOf(
                USER_DATABASE_MIGRATION_126_TO_127,
                USER_DATABASE_MIGRATION_127_TO_128,
                USER_DATABASE_MIGRATION_128_TO_129
        )
    }
}
