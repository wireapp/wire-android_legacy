package com.wire.roomdb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UserPreference::class], version = 1)
abstract class UserDatabase : RoomDatabase() {

    abstract fun userPreferencesDao(): UserPreferencesDao

    companion object {
        @Volatile
        private var INSTANCE_MAP : MutableMap<String, UserDatabase> = mutableMapOf()

        //TODO: better name for db
        @JvmStatic
        fun getInstance(context: Context, userId: String): UserDatabase =
            INSTANCE_MAP[userId] ?: synchronized(this) {
                INSTANCE_MAP[userId] ?: buildDatabase(context, "NEW_DB_$userId").also {
                    INSTANCE_MAP[userId] = it
                }
            }

        private fun buildDatabase(context: Context, dbName: String): UserDatabase =
            Room.databaseBuilder(context.applicationContext, UserDatabase::class.java, dbName).build()
    }

}
