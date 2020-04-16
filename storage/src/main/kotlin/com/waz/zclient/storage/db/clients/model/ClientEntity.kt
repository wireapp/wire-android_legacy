package com.waz.zclient.storage.db.clients.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "client")
data class ClientEntity(
    @ColumnInfo(name = "id")
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "time", defaultValue = "")
    val time: String,

    @ColumnInfo(name = "label", defaultValue = "")
    val label: String,

    @ColumnInfo(name = "type", defaultValue = "")
    val type: String,

    @ColumnInfo(name = "class", defaultValue = "")
    val clazz: String,

    @ColumnInfo(name = "model", defaultValue = "")
    val model: String,

    @ColumnInfo(name = "lat", defaultValue = "0")
    val lat: Double,

    @ColumnInfo(name = "lon", defaultValue = "0")
    val lon: Double,

    @ColumnInfo(name = "locationName")
    val locationName: String?,

    @ColumnInfo(name = "verification", defaultValue = "")
    val verification: String,

    @ColumnInfo(name = "encKey", defaultValue = "")
    val encKey: String,

    @ColumnInfo(name = "macKey", defaultValue = "")
    val macKey: String
)
