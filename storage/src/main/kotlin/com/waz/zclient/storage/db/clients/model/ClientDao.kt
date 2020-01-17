package com.waz.zclient.storage.db.clients.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "client")
data class ClientDao(
    @ColumnInfo(name = "id")
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "time")
    val time: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "class")
    val _class: String,

    @ColumnInfo(name = "model")
    val model: String,

    @ColumnInfo(name = "lat")
    val lat: Double,

    @ColumnInfo(name = "lon")
    val lon: Double,

    @ColumnInfo(name = "locationName")
    val locationName: String?,

    @ColumnInfo(name = "verification")
    val verification: String,

    @ColumnInfo(name = "encKey")
    val encKey: String,

    @ColumnInfo(name = "macKey")
    val macKey: String
)
