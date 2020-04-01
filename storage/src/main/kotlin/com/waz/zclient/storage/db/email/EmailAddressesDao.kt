package com.waz.zclient.storage.db.email

import androidx.room.Dao
import androidx.room.Query

@Dao
interface EmailAddressesDao {
    @Query("SELECT * FROM EmailAddresses")
    suspend fun allEmailAddresses(): List<EmailAddressesEntity>
}
