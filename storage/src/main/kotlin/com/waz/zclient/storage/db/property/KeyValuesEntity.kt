package com.waz.zclient.storage.db.property

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "KeyValues")
data class KeyValuesEntity(

    @ColumnInfo(name = "key")
    @PrimaryKey
    val key: String,

    @ColumnInfo(name = "value", defaultValue = "")
    val value: String
)
