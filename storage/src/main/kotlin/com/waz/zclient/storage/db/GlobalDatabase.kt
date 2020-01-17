package com.waz.zclient.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.waz.zclient.storage.db.accountdata.AccessTokenConverter
import com.waz.zclient.storage.db.accountdata.ActiveAccountsDao
import com.waz.zclient.storage.db.accountdata.ActiveAccountsTable
import com.waz.zclient.storage.db.accountdata.SsoIdConverter

@Database(entities = [ActiveAccountsTable::class], version = 25)
@TypeConverters(value = [AccessTokenConverter::class, SsoIdConverter::class])
abstract class GlobalDatabase: RoomDatabase() {

    abstract fun activeAccountsDao(): ActiveAccountsDao

    companion object {
        const val DB_NAME = "ZGlobal.db"
    }
}
