package com.waz.zclient.roomdb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.zclient.roomdb.dao.UserDao
import com.waz.zclient.roomdb.dao.UserPreferencesDao
import com.waz.zclient.roomdb.model.UserEntity
import com.waz.zclient.roomdb.model.UserPreferenceEntity

@Database(entities = [UserPreferenceEntity::class, UserEntity::class], version = 125, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {

    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var userDatabaseMap: MutableMap<String, UserDatabase> = mutableMapOf()

        @JvmStatic
        fun getInstance(context: Context, userId: String): UserDatabase =
            userDatabaseMap[userId] ?: synchronized(this) {
                userDatabaseMap[userId] ?: buildDatabase(context, "$userId").also {
                    userDatabaseMap[userId] = it
                }
            }

        private fun buildDatabase(context: Context, dbName: String): UserDatabase =
            Room.databaseBuilder(context.applicationContext, UserDatabase::class.java, dbName).addMigrations(migration).build()

         val migration = object:  Migration(124,125){
            override fun migrate(database: SupportSQLiteDatabase) {
              
            }
        }
    }


}
