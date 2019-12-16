package com.waz.zclient.storage.db.users.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preference")
data class UserPreferenceDao(

    @ColumnInfo(name = "key")
    @PrimaryKey
    val key: String,

    @ColumnInfo(name = "value")
    val value: String?
)
