package com.waz.zclient.storage.db.userclients

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

//TODO: replaced by ClientsEntity in Kotlin side??
@Entity(tableName = "Clients")
data class UserClientsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "data", defaultValue = "")
    val data: String
)
