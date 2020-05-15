package com.waz.zclient.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.waz.zclient.storage.db.accountdata.AccessTokenConverter
import com.waz.zclient.storage.db.accountdata.ActiveAccountsDao
import com.waz.zclient.storage.db.accountdata.GLOBAL_DATABASE_MIGRATION_24_25
import com.waz.zclient.storage.db.accountdata.GLOBAL_DATABASE_MIGRATION_25_26
import com.waz.zclient.storage.db.accountdata.ActiveAccountsEntity
import com.waz.zclient.storage.db.accountdata.SsoIdConverter
import com.waz.zclient.storage.db.cache.CacheEntryDao
import com.waz.zclient.storage.db.cache.CacheEntryEntity
import com.waz.zclient.storage.db.teams.TeamsDao
import com.waz.zclient.storage.db.teams.TeamsEntity

@Database(entities = [
    ActiveAccountsEntity::class,
    CacheEntryEntity::class,
    TeamsEntity::class
], version = GlobalDatabase.VERSION)
@TypeConverters(value = [AccessTokenConverter::class, SsoIdConverter::class])
abstract class GlobalDatabase : RoomDatabase() {

    abstract fun activeAccountsDao(): ActiveAccountsDao
    abstract fun cacheEntryDao(): CacheEntryDao
    abstract fun teamsDao(): TeamsDao

    companion object {
        const val DB_NAME = "ZGlobal.db"
        const val VERSION = 26

        @JvmStatic
        val migrations = arrayOf(GLOBAL_DATABASE_MIGRATION_24_25, GLOBAL_DATABASE_MIGRATION_25_26)
    }
}
