package com.waz.zclient.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.waz.zclient.storage.db.assets.AssetsEntity
import com.waz.zclient.storage.db.assets.DownloadAssetsEntity
import com.waz.zclient.storage.db.assets.UploadAssetsEntity
import com.waz.zclient.storage.db.assetsv1.AssetsV1Entity
import com.waz.zclient.storage.db.clients.model.ClientEntity
import com.waz.zclient.storage.db.clients.service.ClientsDao
import com.waz.zclient.storage.db.contacthashes.ContactHashesEntity
import com.waz.zclient.storage.db.contacts.ContactsEntity
import com.waz.zclient.storage.db.contacts.ContactsOnWireEntity
import com.waz.zclient.storage.db.conversationmembers.ConversationMembersEntity
import com.waz.zclient.storage.db.conversations.ConversationFoldersDao
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity
import com.waz.zclient.storage.db.conversations.ConversationMembersDao
import com.waz.zclient.storage.db.conversations.ConversationRoleActionDao
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity
import com.waz.zclient.storage.db.conversations.ConversationsDao
import com.waz.zclient.storage.db.conversations.ConversationsEntity
import com.waz.zclient.storage.db.conversations.EditHistoryEntity
import com.waz.zclient.storage.db.conversations.MessageContentIndexEntity
import com.waz.zclient.storage.db.conversations.ReadReceiptsEntity
import com.waz.zclient.storage.db.email.EmailAddressesEntity
import com.waz.zclient.storage.db.errors.ErrorsDao
import com.waz.zclient.storage.db.errors.ErrorsEntity
import com.waz.zclient.storage.db.folders.FoldersEntity
import com.waz.zclient.storage.db.likes.LikesEntity
import com.waz.zclient.storage.db.messagedeletion.MessageDeletionEntity
import com.waz.zclient.storage.db.messages.MessagesEntity
import com.waz.zclient.storage.db.notifications.CloudNotificationStatsEntity
import com.waz.zclient.storage.db.notifications.CloudNotificationsEntity
import com.waz.zclient.storage.db.notifications.NotificationsEntity
import com.waz.zclient.storage.db.notifications.PushNotificationEventEntity
import com.waz.zclient.storage.db.phonenumbers.PhoneNumbersEntity
import com.waz.zclient.storage.db.properties.PropertiesEntity
import com.waz.zclient.storage.db.sync.SyncJobsDao
import com.waz.zclient.storage.db.sync.SyncJobsEntity
import com.waz.zclient.storage.db.userclients.UserClientsEntity
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.model.UserEntity
import com.waz.zclient.storage.db.users.model.UserPreferenceEntity
import com.waz.zclient.storage.db.users.service.UserDao
import com.waz.zclient.storage.db.users.service.UserPreferenceDao

@Database(
    entities = [UserEntity::class, AssetsV1Entity::class, ConversationsEntity::class, ConversationMembersEntity::class,
        MessagesEntity::class, UserPreferenceEntity::class, SyncJobsEntity::class, ErrorsEntity::class,
        NotificationsEntity::class, ContactHashesEntity::class, ContactsOnWireEntity::class, UserClientsEntity::class,
        ClientEntity::class, LikesEntity::class, ContactsEntity::class, EmailAddressesEntity::class,
        PhoneNumbersEntity::class, MessageDeletionEntity::class, ConversationRoleActionEntity::class,
        ConversationFoldersEntity::class, FoldersEntity::class, CloudNotificationStatsEntity::class,
        CloudNotificationsEntity::class, AssetsEntity::class, DownloadAssetsEntity::class, UploadAssetsEntity::class,
        PropertiesEntity::class, ReadReceiptsEntity::class, PushNotificationEventEntity::class,
        MessageContentIndexEntity::class, EditHistoryEntity::class, ButtonEntity::class],
    version = UserDatabase.VERSION
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun userPreferencesDbService(): UserPreferenceDao
    abstract fun userDbService(): UserDao
    abstract fun clientsDbService(): ClientsDao
    abstract fun syncJobsDao(): SyncJobsDao
    abstract fun errorsDao(): ErrorsDao
    abstract fun conversationFoldersDao(): ConversationFoldersDao
    abstract fun conversationMembersDao(): ConversationMembersDao
    abstract fun conversationRoleActionDao(): ConversationRoleActionDao
    abstract fun conversationsDao(): ConversationsDao

    companion object {
        const val VERSION = 127

        @JvmStatic
        val migrations = arrayOf(USER_DATABASE_MIGRATION_126_TO_127)
    }
}
