package com.waz.zclient.storage.db.contacts

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "ContactsOnWire",
    primaryKeys = ["user", "contact"],
    indices = [Index(name = "ContactsOnWire_contact", value = ["contact"])]
)
data class ContactsOnWireEntity(
    @ColumnInfo(name = "user", defaultValue = "")
    val userId: String,

    @ColumnInfo(name = "contact", defaultValue = "")
    val contactId: String
)
