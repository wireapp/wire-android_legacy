package com.waz.zclient.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.waz.zclient.storage.db.assets.model.AssetsEntity
import com.waz.zclient.storage.db.assets.model.DownloadAssetsEntity
import com.waz.zclient.storage.db.assets.model.UploadAssetsEntity
import com.waz.zclient.storage.db.clients.model.ClientEntity
import com.waz.zclient.storage.db.clients.service.ClientsDao
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity
import com.waz.zclient.storage.db.conversations.ReadReceiptsEntity
import com.waz.zclient.storage.db.folders.FoldersEntity
import com.waz.zclient.storage.db.notifications.CloudNotificationStatsEntity
import com.waz.zclient.storage.db.notifications.CloudNotificationsEntity
import com.waz.zclient.storage.db.notifications.PushNotificationEventEntity
import com.waz.zclient.storage.db.properties.PropertiesEntity
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.model.UserEntity
import com.waz.zclient.storage.db.users.model.UserPreferenceEntity
import com.waz.zclient.storage.db.users.service.UserDao
import com.waz.zclient.storage.db.users.service.UserPreferenceDao

@Database(
    entities = [UserPreferenceEntity::class, UserEntity::class, ClientEntity::class,
        ConversationRoleActionEntity::class, ConversationFoldersEntity::class, FoldersEntity::class,
        CloudNotificationStatsEntity::class, CloudNotificationsEntity::class, AssetsEntity::class,
        DownloadAssetsEntity::class, UploadAssetsEntity::class, PropertiesEntity::class,
        ReadReceiptsEntity::class, PushNotificationEventEntity::class],
    version = UserDatabase.VERSION,
    exportSchema = false
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun userPreferencesDbService(): UserPreferenceDao
    abstract fun userDbService(): UserDao
    abstract fun clientsDbService(): ClientsDao

    companion object {
        const val VERSION = 127

        @JvmStatic
        val migrations = arrayOf(USER_DATABASE_MIGRATION_126_TO_127)
    }
}
