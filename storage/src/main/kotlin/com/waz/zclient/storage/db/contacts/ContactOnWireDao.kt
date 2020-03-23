package com.waz.zclient.storage.db.contacts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ContactOnWireDao {
    @Query("SELECT * FROM ContactsOnWire")
    suspend fun allContactOnWire(): List<ContactsOnWireEntity>

    @Insert
    fun insert(contactsOnWireEntity: ContactsOnWireEntity)
}
