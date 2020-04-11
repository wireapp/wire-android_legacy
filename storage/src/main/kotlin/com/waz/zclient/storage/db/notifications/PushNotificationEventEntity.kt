package com.waz.zclient.storage.db.notifications

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "PushNotificationEvents")
data class PushNotificationEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_index")
    val eventIndex: Int,

    @ColumnInfo(name = "pushId")
    val pushId: String,

    @ColumnInfo(name = "decrypted")
    val isDecrypted: Boolean,

    @ColumnInfo(name = "event")
    val eventJson: String,

    @ColumnInfo(name = "plain", typeAffinity = ColumnInfo.BLOB)
    val plain: ByteArray?,

    @ColumnInfo(name = "transient")
    val isTransient: Boolean
)
