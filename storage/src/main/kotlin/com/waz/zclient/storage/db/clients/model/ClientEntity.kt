package com.waz.zclient.storage.db.clients.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "client")
data class ClientEntity(
    @ColumnInfo(name = "id")
    @PrimaryKey
    @SerializedName("id")
    val id: String,

    @ColumnInfo(name = "time")
    @SerializedName("time")
    val time: String,

    @ColumnInfo(name = "label")
    @SerializedName("label")
    val label: String,

    @ColumnInfo(name = "cookie")
    @SerializedName("cookie")
    val cookie: String?,

    @ColumnInfo(name = "type")
    @SerializedName("type")
    val type: String,

    @ColumnInfo(name = "class")
    @SerializedName("class")
    val _class: String,

    @ColumnInfo(name = "model")
    @SerializedName("model")
    val model: String,

    @ColumnInfo(name = "lat")
    val lat: Double,

    @ColumnInfo(name = "lon")
    val lon: Double,

    @ColumnInfo(name = "encKey")
    val encKey: String,

    @ColumnInfo(name = "macKey")
    val macKey: String,

    @ColumnInfo(name = "locationName")
    val locationName: String,

    @ColumnInfo(name = "verification")
    val verification: String)

data class ClientLocationEntity(
    @ColumnInfo(name = "lon")
    @SerializedName("lon")
    val long: Double,

    @ColumnInfo(name = "lat")
    @SerializedName("lat")
    val lat: Double)
