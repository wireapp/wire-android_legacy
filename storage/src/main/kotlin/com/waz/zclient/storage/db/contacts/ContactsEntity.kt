package com.waz.zclient.storage.db.contacts

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Contacts",
    indices = [Index(name = "Contacts_sorting", value = ["sort_key"])]
)
data class ContactsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "name", defaultValue = "")
    val name: String,

    @ColumnInfo(name = "name_source", defaultValue = "0")
    val nameType: Int,

    @ColumnInfo(name = "sort_key", defaultValue = "")
    val sortKey: String,

    @ColumnInfo(name = "search_key", defaultValue = "")
    val searchKey: String
)
