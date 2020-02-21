package com.waz.zclient.storage.db.email

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "EmailAddresses",
    primaryKeys = ["contact", "email_address"],
    indices = [Index(value = ["contact", "email_address"])]
)
data class EmailAddressesEntity(
    @ColumnInfo(name = "contact")
    val contactId: String,

    @ColumnInfo(name = "email_address")
    val emailAddress: String
)
