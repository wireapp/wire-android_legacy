package com.waz.zclient.storage.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import com.waz.zclient.storage.db.GlobalDatabase
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.pref.backend.BackendPreferences
import com.waz.zclient.storage.pref.global.GlobalPreferences
import com.waz.zclient.storage.pref.user.UserPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val storageModule: Module = module {
    single { GlobalPreferences(androidContext()) }
    single { BackendPreferences(androidContext()) }
    factory {
        StorageModule.getUserDatabase(androidContext(), get<GlobalPreferences>().activeUserId, UserDatabase.migrations)
    }
    single { StorageModule.getGlobalDatabase(androidContext(), GlobalDatabase.migrations) }
    single { UserPreferences(androidContext(), get()) }
}

object StorageModule {

    private val userDatabaseMap = mutableMapOf<String, UserDatabase>()
    private lateinit var globalDatabase: GlobalDatabase

    @Suppress("SpreadOperator")
    @JvmStatic
    fun getUserDatabase(context: Context, dbName: String, migrations: Array<out Migration>): UserDatabase {
        if (!userDatabaseMap.contains(dbName)) {
            userDatabaseMap[dbName] = Room.databaseBuilder(
                context, UserDatabase::class.java, dbName
            ).addMigrations(*migrations).build()
        }
        return userDatabaseMap[dbName]!!
    }

    @Suppress("SpreadOperator")
    @JvmStatic
    fun getGlobalDatabase(context: Context, migrations: Array<out Migration>): GlobalDatabase {
        if (!this::globalDatabase.isInitialized) {
            globalDatabase = Room.databaseBuilder(context,
                GlobalDatabase::class.java,
                GlobalDatabase.DB_NAME
            ).addMigrations(*migrations).build()
        }
        return globalDatabase
    }
}
