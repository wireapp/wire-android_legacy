package com.waz.zclient.storage.db.notifications

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "EncryptedPushNotificationEvents", primaryKeys = ["pushId", "event_index"])
data class EncryptedPushNotificationEventEntity(

    @ColumnInfo(name = "pushId")
    val pushId: String,

    @ColumnInfo(name = "event_index", defaultValue = "0")
    val eventIndex: Int,

    @ColumnInfo(name = "event", defaultValue = "")
    val eventJson: String,

    @ColumnInfo(name = "transient", defaultValue = "0")
    val isTransient: Boolean
)
