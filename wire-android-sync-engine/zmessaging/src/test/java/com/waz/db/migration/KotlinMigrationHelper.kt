package com.waz.db.migration

import com.waz.zclient.storage.db.GlobalDatabase
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.accountdata.ActiveAccountsEntity
import com.waz.zclient.storage.db.assets.AssetsEntity
import com.waz.zclient.storage.db.assets.AssetsV1Entity
import com.waz.zclient.storage.db.assets.DownloadAssetsEntity
import com.waz.zclient.storage.db.assets.UploadAssetsEntity
import com.waz.zclient.storage.db.cache.CacheEntryEntity
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity
import com.waz.zclient.storage.db.conversations.ConversationMembersEntity
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity
import com.waz.zclient.storage.db.conversations.ConversationsEntity
import com.waz.zclient.storage.db.errors.ErrorsEntity
import com.waz.zclient.storage.db.folders.FoldersEntity
import com.waz.zclient.storage.db.history.EditHistoryEntity
import com.waz.zclient.storage.db.messages.LikesEntity
import com.waz.zclient.storage.db.messages.MessageContentIndexEntity
import com.waz.zclient.storage.db.messages.MessageDeletionEntity
import com.waz.zclient.storage.db.messages.MessagesEntity
import com.waz.zclient.storage.db.notifications.CloudNotificationStatsEntity
import com.waz.zclient.storage.db.notifications.CloudNotificationsEntity
import com.waz.zclient.storage.db.notifications.NotificationDataEntity
import com.waz.zclient.storage.db.notifications.PushNotificationEventEntity
import com.waz.zclient.storage.db.property.KeyValuesEntity
import com.waz.zclient.storage.db.property.PropertiesEntity
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity
import com.waz.zclient.storage.db.sync.SyncJobsEntity
import com.waz.zclient.storage.db.teams.TeamsEntity
import com.waz.zclient.storage.db.userclients.UserClientsEntity
import com.waz.zclient.storage.db.users.model.UserEntity
import kotlinx.coroutines.runBlocking

object KotlinMigrationHelper {

    @JvmStatic
    fun assertAssetsEntity(roomDB: UserDatabase, assetsEntity: AssetsEntity) =
        runBlocking {
            val entity = roomDB.assetsDao().allAssets()[0]
            check(assetsEntity, entity)
        }

    @JvmStatic
    fun assertAssetV1Entity(roomDB: UserDatabase, assetsV1Entity: AssetsV1Entity) =
        runBlocking {
            val entity = roomDB.assetsV1Dao().allAssets()[0]
            check(assetsV1Entity, entity)
        }

    @JvmStatic
    fun assertConvFoldersEntity(roomDB: UserDatabase, convFoldersEntity: ConversationFoldersEntity) =
        runBlocking {
            val entity = roomDB.conversationFoldersDao().allConversationFolders()[0]
            check(convFoldersEntity, entity)
        }

    @JvmStatic
    fun assertConvMembersEntity(roomDB: UserDatabase, membersEntity: ConversationMembersEntity) =
        runBlocking {
            val entity = roomDB.conversationMembersDao().allConversationMembers()[0]
            check(membersEntity, entity)
        }

    @JvmStatic
    fun assertConvRoleActionEntity(roomDB: UserDatabase, actionEntity: ConversationRoleActionEntity) =
        runBlocking {
            val entity = roomDB.conversationRoleActionDao().allConversationRoleActions()[0]
            check(actionEntity, entity)
        }

    @JvmStatic
    fun assertConversationsEntity(roomDB: UserDatabase, conversationsEntity: ConversationsEntity) =
        runBlocking {
            val entity = roomDB.conversationsDao().allConversations()[0]
            check(conversationsEntity, entity)
        }

    @JvmStatic
    fun assertDownloadAssetsEntity(roomDB: UserDatabase, downloadAssetsEntity: DownloadAssetsEntity) =
        runBlocking {
            val entity = roomDB.downloadAssetsDao().allDownloadAssets()[0]
            check(downloadAssetsEntity, entity)
        }

    @JvmStatic
    fun assertEditHistoryEntity(roomDB: UserDatabase, editHistoryEntity: EditHistoryEntity) =
        runBlocking {
            val entity = roomDB.editHistoryDao().allHistory()[0]
            check(editHistoryEntity, entity)
        }

    @JvmStatic
    fun assertErrorsEntity(roomDB: UserDatabase, errorsEntity: ErrorsEntity) =
        runBlocking {
            val entity = roomDB.errorsDao().allErrors()[0]
            check(errorsEntity, entity)
        }

    @JvmStatic
    fun assertCloudNotificationsEntity(roomDB: UserDatabase, cloudNotEntity: CloudNotificationsEntity) =
        runBlocking {
            val entity = roomDB.cloudNotificationsDao().allCloudNotifications()[0]
            check(cloudNotEntity, entity)
        }

    @JvmStatic
    fun assertCloudNotificationStatsEntity(roomDB: UserDatabase, statsEntity: CloudNotificationStatsEntity) =
        runBlocking {
            val entity = roomDB.cloudNotificationStatsDao().allCloudNotificationStats()[0]
            check(statsEntity, entity)
        }

    @JvmStatic
    fun assertFoldersEntity(roomDB: UserDatabase, foldersEntity: FoldersEntity) =
        runBlocking {
            val entity = roomDB.foldersDao().allFolders()[0]
            check(foldersEntity, entity)
        }

    @JvmStatic
    fun assertKeyValuesEntity(roomDB: UserDatabase, keyValuesEntity: KeyValuesEntity) =
        runBlocking {
            val entity = roomDB.keyValuesDao().allKeyValues()[0]
            check(keyValuesEntity, entity)
        }

    @JvmStatic
    fun assertLikesEntity(roomDB: UserDatabase, likesEntity: LikesEntity) =
        runBlocking {
            val entity = roomDB.likesDao().allLikes()[0]
            check(likesEntity, entity)
        }

    @JvmStatic
    fun assertMessageContentIndexEntity(roomDB: UserDatabase, indexEntity: MessageContentIndexEntity) =
        runBlocking {
            val entity = roomDB.messageContentIndexDao().allMessageContentIndexes()[0]
            check(indexEntity, entity)
        }

    @JvmStatic
    fun assertMessageDeletionEntity(roomDB: UserDatabase, msgDeletionEntity: MessageDeletionEntity) =
        runBlocking {
            val entity = roomDB.messagesDeletionDao().allMessageDeletions()[0]
            check(msgDeletionEntity, entity)
        }

    @JvmStatic
    fun assertMessageEntity(roomDB: UserDatabase, messagesEntity: MessagesEntity) =
        runBlocking {
            val entity = roomDB.messagesDao().allMessages()[0]
            check(messagesEntity, entity)
        }

    @JvmStatic
    fun assertNotificationDataEntity(roomDB: UserDatabase, notEntity: NotificationDataEntity) =
        runBlocking {
            val entity = roomDB.notificationDataDao().allNotificationsData()[0]
            check(notEntity, entity)
        }

    @JvmStatic
    fun assertPropertiesEntity(roomDB: UserDatabase, propertiesEntity: PropertiesEntity) =
        runBlocking {
            val entity = roomDB.propertiesDao().allProperties()[0]
            check(propertiesEntity, entity)
        }

    @JvmStatic
    fun assertPushNotEventEntity(roomDB: UserDatabase, pushEventEntity: PushNotificationEventEntity) =
        runBlocking {
            val entity = roomDB.pushNotificationEventDao().allPushNotificationEvents()[0]
            check(pushEventEntity, entity)
        }

    @JvmStatic
    fun assertReadReceiptsEntity(roomDB: UserDatabase, readReceiptsEntity: ReadReceiptsEntity) =
        runBlocking {
            val entity = roomDB.readReceiptsDao().allReceipts()[0]
            check(readReceiptsEntity, entity)
        }

    @JvmStatic
    fun assertSyncJobsEntity(roomDB: UserDatabase, syncJobsEntity: SyncJobsEntity) =
        runBlocking {
            val entity = roomDB.syncJobsDao().allSyncJobs()[0]
            check(syncJobsEntity, entity)
        }

    @JvmStatic
    fun assertUploadAssetEntity(roomDB: UserDatabase, uploadAssetsEntity: UploadAssetsEntity) =
        runBlocking {
            val entity = roomDB.uploadAssetsDao().allUploadAssets()[0]
            check(uploadAssetsEntity, entity)
        }

    @JvmStatic
    fun assertUserEntity(roomDB: UserDatabase, userEntity: UserEntity) =
        runBlocking {
            val entity = roomDB.userDao().allUsers()[0]
            check(userEntity, entity)
        }

    @JvmStatic
    fun assertUserClientEntity(roomDB: UserDatabase, userClientsEntity: UserClientsEntity) =
        runBlocking {
            val entity = roomDB.userClientDao().allClients()[0]
            check(userClientsEntity, entity)
        }

    @JvmStatic
    fun assertActiveAccountsEntity(roomDB: GlobalDatabase, accountsEntity: ActiveAccountsEntity) =
        runBlocking {
            val entity = roomDB.activeAccountsDao().activeAccounts()[0]
            check(accountsEntity, entity)
        }

    @JvmStatic
    fun assertCacheEntryEntity(roomDB: GlobalDatabase, cacheEntryEntity: CacheEntryEntity) =
        runBlocking {
            val entity = roomDB.cacheEntryDao().cacheEntries()[0]
            check(cacheEntryEntity, entity)
        }

    @JvmStatic
    fun assertTeamsEntity(roomDB: GlobalDatabase, teamsEntity: TeamsEntity) =
        runBlocking {
            val entity = roomDB.teamsDao().allTeams()[0]
            check(teamsEntity, entity)
        }

    private fun <T> check(expected: T, got: T): Boolean {
        val result = expected == got
        if (!result) {
            println("Expected:   $expected \nGot result: $got")
        }
        return result
    }
}
