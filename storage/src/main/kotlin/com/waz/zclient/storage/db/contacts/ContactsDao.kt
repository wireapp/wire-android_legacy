package com.waz.zclient.storage.db.contacts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ContactsDao {
    @Query("SELECT * FROM Contacts")
    suspend fun allContacts(): List<ContactsEntity>

    @Insert
    fun insert(contactsEntity: ContactsEntity)
}
