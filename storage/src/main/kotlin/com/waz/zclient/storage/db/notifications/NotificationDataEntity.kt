package com.waz.zclient.storage.db.notifications

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "NotificationData")
data class NotificationDataEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "data", defaultValue = "")
    val data: String
)
