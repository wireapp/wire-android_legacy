package com.waz.zclient.storage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.waz.zclient.storage.db.clients.model.ClientDao
import com.waz.zclient.storage.db.clients.service.ClientDbService
import com.waz.zclient.storage.db.users.migration.UserDatabaseMigration
import com.waz.zclient.storage.db.users.model.UserDao
import com.waz.zclient.storage.db.users.model.UserPreferenceDao
import com.waz.zclient.storage.db.users.service.UserDbService
import com.waz.zclient.storage.db.users.service.UserPreferenceDbService

@Database(entities = [UserPreferenceDao::class, UserDao::class, ClientDao::class], version = 126, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {

    abstract fun userPreferencesDao(): UserPreferenceDbService
    abstract fun userDbService(): UserDbService
    abstract fun clientsDao(): ClientDbService

    companion object {

        @Volatile
        private var userDatabaseMap: MutableMap<String, UserDatabase> = mutableMapOf()

        @JvmStatic
        fun getInstance(context: Context, userId: String): UserDatabase =
            userDatabaseMap[userId] ?: synchronized(this) {
                userDatabaseMap[userId] ?: buildDatabase(context, userId).also {
                    userDatabaseMap[userId] = it
                }
            }

        private fun buildDatabase(context: Context, dbName: String): UserDatabase =
            Room.databaseBuilder(context.applicationContext, UserDatabase::class.java, dbName)
                .addMigrations(UserDatabaseMigration())
                .build()
    }


}
