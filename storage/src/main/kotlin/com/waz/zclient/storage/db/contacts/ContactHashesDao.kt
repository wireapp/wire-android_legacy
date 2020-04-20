package com.waz.zclient.storage.db.contacts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ContactHashesDao {
    @Query("SELECT * FROM ContactHashes")
    suspend fun allContactHashes(): List<ContactHashesEntity>

    @Insert
    fun insert(contactHashesEntity: ContactHashesEntity)
}
