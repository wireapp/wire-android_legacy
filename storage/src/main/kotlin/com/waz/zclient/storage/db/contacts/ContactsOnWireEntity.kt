package com.waz.zclient.storage.db.contacts

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "ContactsOnWire", primaryKeys = ["user", "contact"])
data class ContactsOnWireEntity(
    @ColumnInfo(name = "user")
    val userId: String,

    @ColumnInfo(name = "contact")
    val contactId: String
)
