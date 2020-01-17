package com.waz.zclient.storage.di

import androidx.room.Room
import com.waz.zclient.storage.db.GlobalDatabase
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.accountdata.GLOBAL_MIGRATION_24_25
import com.waz.zclient.storage.db.users.migration.UserDatabaseMigration
import com.waz.zclient.storage.pref.GlobalPreferences
import com.waz.zclient.storage.pref.UserPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val storageModule: Module = module {
    single { GlobalPreferences(androidContext()) }
    single {
        Room.databaseBuilder(androidContext(),
            UserDatabase::class.java, get<GlobalPreferences>().activeUserId)
            .addMigrations(UserDatabaseMigration()).build()
    }
    single {
        Room.databaseBuilder(
            androidContext(),
            GlobalDatabase::class.java,
            GlobalDatabase.DB_NAME
        ).addMigrations(GLOBAL_MIGRATION_24_25).build()
    }
    single { UserPreferences(androidContext(), get()) }
    single {}
}
