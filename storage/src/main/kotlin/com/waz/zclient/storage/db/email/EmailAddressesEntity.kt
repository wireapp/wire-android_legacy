package com.waz.zclient.storage.db.email

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "EmailAddresses",
    primaryKeys = ["contact", "email_address"],
    indices = [
        Index(name = "EmailAddresses_contact", value = ["contact"]),
        Index(name = "EmailAddresses_email", value = ["email_address"])
    ]
)
data class EmailAddressesEntity(
    @ColumnInfo(name = "contact", defaultValue = "")
    val contactId: String,

    @ColumnInfo(name = "email_address", defaultValue = "")
    val emailAddress: String
)
