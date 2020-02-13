package com.waz.zclient.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.waz.zclient.storage.db.clients.model.ClientEntity
import com.waz.zclient.storage.db.clients.service.ClientsDao
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.model.UserEntity
import com.waz.zclient.storage.db.users.model.UserPreferenceEntity
import com.waz.zclient.storage.db.users.service.UserDao
import com.waz.zclient.storage.db.users.service.UserPreferenceDao

@Database(
    entities = [UserPreferenceEntity::class, UserEntity::class, ClientEntity::class],
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
