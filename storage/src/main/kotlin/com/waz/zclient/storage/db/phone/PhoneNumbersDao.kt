package com.waz.zclient.storage.db.phone

import androidx.room.Dao
import androidx.room.Query

@Dao
interface PhoneNumbersDao {
    @Query("SELECT * FROM PhoneNumbers")
    suspend fun allPhoneNumbers(): List<PhoneNumbersEntity>
}
