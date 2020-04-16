package com.waz.zclient.storage.db.property

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Properties")
data class PropertiesEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value", defaultValue = "")
    val value: String
)
