package com.waz.zclient.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.waz.zclient.storage.db.clients.model.ClientDao
import com.waz.zclient.storage.db.clients.service.ClientDbService
import com.waz.zclient.storage.db.users.model.UserDao
import com.waz.zclient.storage.db.users.model.UserPreferenceDao
import com.waz.zclient.storage.db.users.service.UserDbService
import com.waz.zclient.storage.db.users.service.UserPreferenceDbService

@Database(entities = [UserPreferenceDao::class, UserDao::class, ClientDao::class], version = 126, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {

    abstract fun userPreferencesDbService(): UserPreferenceDbService
    abstract fun userDbService(): UserDbService
    abstract fun clientsDbService(): ClientDbService

}
