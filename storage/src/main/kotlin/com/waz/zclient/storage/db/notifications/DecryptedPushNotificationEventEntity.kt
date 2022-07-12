package com.waz.zclient.storage.db.notifications

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DecryptedPushNotificationEvents")
data class DecryptedPushNotificationEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "pushId")
    val pushId: String,

    @ColumnInfo(name = "event_index", defaultValue = "0")
    val eventIndex: Int,

    @ColumnInfo(name = "event", defaultValue = "")
    val eventJson: String,

    @ColumnInfo(name = "plain", typeAffinity = ColumnInfo.BLOB)
    val plain: ByteArray?,

    @ColumnInfo(name = "transient", defaultValue = "0")
    val isTransient: Boolean
)
