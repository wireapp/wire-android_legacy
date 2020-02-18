package com.waz.zclient.storage.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import com.waz.zclient.storage.db.GlobalDatabase
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.pref.GlobalPreferences
import com.waz.zclient.storage.pref.UserPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val storageModule: Module = module {
    single { GlobalPreferences(androidContext()) }
    single { StorageModule.createUserDatabase(androidContext(), get<GlobalPreferences>().activeUserId, UserDatabase.migrations) }
    single { StorageModule.createGlobalDatabase(androidContext(), GlobalDatabase.migrations) }
    single { UserPreferences(androidContext(), get()) }
}

object StorageModule {

    @Suppress("SpreadOperator")
    @JvmStatic
    fun createUserDatabase(context: Context, dbName: String, migrations: Array<out Migration>) =
        Room.databaseBuilder(context,
            UserDatabase::class.java,
            dbName
        ).addMigrations(*migrations).build()

    @Suppress("SpreadOperator")
    @JvmStatic
    fun createGlobalDatabase(context: Context, migrations: Array<out Migration>) =
        Room.databaseBuilder(
            context,
            GlobalDatabase::class.java,
            GlobalDatabase.DB_NAME
        ).addMigrations(*migrations).build()
}
