package com.waz.zclient.storage.db.phonenumbers

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "PhoneNumbers",
    primaryKeys = ["contact", "phone_number"],
    indices = [Index(value = ["contact", "phone_number"])]
)
data class PhoneNumbersEntity(
    @ColumnInfo(name = "contact")
    val contactId: String,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String
)
