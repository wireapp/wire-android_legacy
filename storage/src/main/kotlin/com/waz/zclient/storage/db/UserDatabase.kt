package com.waz.zclient.storage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.waz.zclient.storage.clients.dao.ClientDao
import com.waz.zclient.storage.clients.migration.ClientsTableMigration
import com.waz.zclient.storage.clients.model.ClientEntity
import com.waz.zclient.storage.db.dao.UserDao
import com.waz.zclient.storage.db.dao.UserPreferencesDao
import com.waz.zclient.storage.db.migration.UserDatabaseMigration
import com.waz.zclient.storage.db.model.UserEntity
import com.waz.zclient.storage.db.model.UserPreferenceEntity

@Database(entities = [UserPreferenceEntity::class, UserEntity::class, ClientEntity::class], version = 125, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {

    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun userDao(): UserDao
    abstract fun clientsDao(): ClientDao

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
                .addMigrations(UserDatabaseMigration(), ClientsTableMigration()).build()
    }


}
