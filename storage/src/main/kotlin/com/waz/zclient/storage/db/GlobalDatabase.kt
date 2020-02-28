package com.waz.zclient.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.waz.zclient.storage.db.accountdata.ACTIVE_ACCOUNTS_MIGRATION
import com.waz.zclient.storage.db.accountdata.AccessTokenConverter
import com.waz.zclient.storage.db.accountdata.ActiveAccountsDao
import com.waz.zclient.storage.db.accountdata.ActiveAccountsEntity
import com.waz.zclient.storage.db.accountdata.SsoIdConverter

@Database(
    entities = [ActiveAccountsEntity::class],
    version = GlobalDatabase.VERSION,
    exportSchema = false
)
@TypeConverters(value = [AccessTokenConverter::class, SsoIdConverter::class])
abstract class GlobalDatabase : RoomDatabase() {

    abstract fun activeAccountsDao(): ActiveAccountsDao

    companion object {
        const val DB_NAME = "ZGlobal.db"
        const val VERSION = 25

        @JvmStatic
        val migrations = arrayOf(ACTIVE_ACCOUNTS_MIGRATION)
    }
}
